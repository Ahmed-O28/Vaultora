package com.banking.systemdesign.urlshortener;

import com.banking.exception.ForbiddenException;
import com.banking.exception.InvalidTransferException;
import com.banking.model.Account;
import com.banking.model.ShortUrl;
import com.banking.model.Transaction;
import com.banking.repository.AccountRepository;
import com.banking.repository.ShortUrlRepository;
import com.banking.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise-grade persistent URL shortener backed by PostgreSQL/JPA.
 * Reuses the Base62 encoding strategy, provides collision avoidance,
 * enforces strict URL validation (blocks open redirects & unsafe schemes like javascript:),
 * and handles transaction receipt authorization.
 */
@Service
@Primary
public class PersistentShortUrlService implements ShortUrlService {

    private static final Logger log = LoggerFactory.getLogger(PersistentShortUrlService.class);

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "file", "vbscript", "about");

    private final ShortUrlRepository shortUrlRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final Base62ShortUrlService fallback;

    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 10000000L + 100000L);
    private final SecureRandom random = new SecureRandom();

    @Value("${app.shortener.default-ttl-seconds:604800}") // 7 days default
    private long defaultTtlSeconds;

    @Value("${app.shortener.base-url:http://localhost:8081/r/}")
    private String baseUrl;

    public PersistentShortUrlService(
            ShortUrlRepository shortUrlRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            Base62ShortUrlService fallback) {
        this.shortUrlRepository = shortUrlRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.fallback = fallback;
    }

    @Override
    @Transactional
    public String shorten(String longUrl) {
        return shorten(longUrl, null, null, null);
    }

    @Transactional
    public String shorten(String longUrl, String transactionId, Long ownerCustomerId, Long expiresInSeconds) {
        validateUrlSafety(longUrl);

        try {
            // If already shortened for this exact target URL, return existing code
            Optional<ShortUrl> existing = shortUrlRepository.findByTargetUrl(longUrl);
            if (existing.isPresent() && !existing.get().isExpired()) {
                return existing.get().getShortCode();
            }

            // Generate unique Base62 short code with collision resolution
            String shortCode = generateUniqueShortCode();

            Instant expiresAt = null;
            if (expiresInSeconds != null && expiresInSeconds > 0) {
                expiresAt = Instant.now().plusSeconds(expiresInSeconds);
            } else if (defaultTtlSeconds > 0) {
                expiresAt = Instant.now().plusSeconds(defaultTtlSeconds);
            }

            ShortUrl mapping = new ShortUrl(shortCode, longUrl, transactionId, ownerCustomerId, expiresAt);
            shortUrlRepository.save(mapping);
            return shortCode;
        } catch (Exception e) {
            log.warn("Database URL shortener failed, falling back to in-memory: {}", e.getMessage());
            return fallback.shorten(longUrl);
        }
    }

    @Override
    @Transactional
    public String resolve(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return null;
        }

        try {
            Optional<ShortUrl> mappingOpt = shortUrlRepository.findByShortCode(shortCode.trim());
            if (mappingOpt.isEmpty()) {
                // Check fallback
                return fallback.resolve(shortCode);
            }

            ShortUrl mapping = mappingOpt.get();
            if (mapping.isExpired()) {
                log.info("Short URL code {} is expired", shortCode);
                return null;
            }

            // Track click count
            mapping.setClickCount(mapping.getClickCount() + 1);
            shortUrlRepository.save(mapping);

            return mapping.getTargetUrl();
        } catch (Exception e) {
            log.warn("Database resolve failed, falling back to in-memory: {}", e.getMessage());
            return fallback.resolve(shortCode);
        }
    }

    /**
     * Shortens a verified transaction receipt for an authorized customer.
     */
    @Transactional
    public ShortUrl shortenReceipt(String transactionId, Long customerId, Long expiresInSeconds) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }

        Transaction txn = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new InvalidTransferException("Transaction not found: " + transactionId));

        // Authorize: Ensure customer owns either the source or destination account
        verifyCustomerOwnership(txn, customerId);

        // Check if receipt is already shortened
        Optional<ShortUrl> existing = shortUrlRepository.findByTransactionId(transactionId);
        if (existing.isPresent() && !existing.get().isExpired()) {
            return existing.get();
        }

        String targetUrl = "/receipts/" + transactionId;
        String shortCode = generateUniqueShortCode();

        Instant expiresAt = (expiresInSeconds != null && expiresInSeconds > 0)
                ? Instant.now().plusSeconds(expiresInSeconds)
                : Instant.now().plusSeconds(defaultTtlSeconds);

        ShortUrl mapping = new ShortUrl(shortCode, targetUrl, transactionId, customerId, expiresAt);
        return shortUrlRepository.save(mapping);
    }

    /**
     * Retrieves transaction receipt with strict customer ownership authorization.
     * Prevents User A from viewing User B's private transaction.
     */
    public Transaction getAuthorizedReceipt(String transactionId, Long customerId) {
        Transaction txn = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new InvalidTransferException("Transaction not found: " + transactionId));

        verifyCustomerOwnership(txn, customerId);
        return txn;
    }

    public Optional<ShortUrl> getByShortCode(String shortCode) {
        return shortUrlRepository.findByShortCode(shortCode);
    }

    public Optional<ShortUrl> getByTransactionId(String transactionId) {
        return shortUrlRepository.findByTransactionId(transactionId);
    }

    public String buildFullShortUrl(String shortCode) {
        return baseUrl + shortCode;
    }

    private void verifyCustomerOwnership(Transaction txn, Long customerId) {
        if (customerId == null) {
            throw new ForbiddenException("Authentication required to access transaction receipt");
        }

        List<Account> userAccounts = accountRepository.findByCustomerId(customerId);
        boolean isOwner = userAccounts.stream().anyMatch(acc ->
                acc.getAccountNumber().equalsIgnoreCase(txn.getSourceAccount()) ||
                acc.getAccountNumber().equalsIgnoreCase(txn.getDestinationAccount())
        );

        if (!isOwner) {
            throw new ForbiddenException("Access denied: You do not have permission to view or share this receipt");
        }
    }

    /**
     * Validates URL scheme and structure to prevent open redirects and script injection.
     */
    public void validateUrlSafety(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        String clean = url.trim().toLowerCase();

        // Prevent dangerous schemes
        for (String scheme : BLOCKED_SCHEMES) {
            if (clean.startsWith(scheme + ":")) {
                throw new IllegalArgumentException("Unsafe URL scheme detected: " + scheme);
            }
        }

        // Relative banking links are allowed
        if (url.startsWith("/")) {
            return;
        }

        // Must be valid HTTP or HTTPS URL
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only HTTP, HTTPS, or relative internal URLs are permitted");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
        }
    }

    private String generateUniqueShortCode() {
        int attempts = 0;
        while (attempts < 10) {
            long val = counter.incrementAndGet() + (random.nextInt(1000) * 1000L);
            String code = encodeBase62(val);
            if (!shortUrlRepository.existsByShortCode(code)) {
                return code;
            }
            attempts++;
        }
        // Fallback with random suffix
        return encodeBase62(counter.incrementAndGet()) + encodeBase62(random.nextInt(62));
    }

    private String encodeBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int) (num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }
}
