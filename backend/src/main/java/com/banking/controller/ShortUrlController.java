package com.banking.controller;

import com.banking.dto.ReceiptResponse;
import com.banking.dto.ShortUrlResponse;
import com.banking.dto.ShortenRequest;
import com.banking.exception.InvalidTransferException;
import com.banking.model.ShortUrl;
import com.banking.model.Transaction;
import com.banking.service.JwtService;
import com.banking.systemdesign.urlshortener.PersistentShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class ShortUrlController {

    private final PersistentShortUrlService shortUrlService;
    private final JwtService jwtService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public ShortUrlController(PersistentShortUrlService shortUrlService, JwtService jwtService) {
        this.shortUrlService = shortUrlService;
        this.jwtService = jwtService;
    }

    /**
     * Creates a shortened URL for any valid target URL or authorized transaction receipt.
     */
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortUrlResponse> shortenUrl(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        Long customerId = extractCustomerIdFromRequest(httpRequest);

        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            // Receipt shortening requires authenticated customer
            ShortUrl mapping = shortUrlService.shortenReceipt(
                    request.getTransactionId().trim(),
                    customerId,
                    request.getExpiresInSeconds()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(mapping));
        }

        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("Either 'url' or 'transactionId' must be provided");
        }

        String shortCode = shortUrlService.shorten(
                request.getUrl().trim(),
                null,
                customerId,
                request.getExpiresInSeconds()
        );

        Optional<ShortUrl> mappingOpt = shortUrlService.getByShortCode(shortCode);
        if (mappingOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(mappingOpt.get()));
        }

        ShortUrlResponse simpleResp = new ShortUrlResponse(
                shortCode,
                shortUrlService.buildFullShortUrl(shortCode),
                request.getUrl(),
                null,
                null,
                0
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResp);
    }

    /**
     * Resolves a short code and redirects to the target destination.
     * Accessible at /r/{shortCode}. Internal routes (e.g. /receipts/:txnId)
     * are resolved to the frontend origin.
     */
    @GetMapping("/r/{shortCode}")
    public ResponseEntity<?> resolveAndRedirect(@PathVariable String shortCode) {
        String targetUrl = shortUrlService.resolve(shortCode);
        if (targetUrl == null) {
            // Check if expired vs non-existent
            Optional<ShortUrl> mapping = shortUrlService.getByShortCode(shortCode);
            if (mapping.isPresent() && mapping.get().isExpired()) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body("The requested short link has expired.");
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Short link not found: " + shortCode);
        }

        // Return HTTP 302 Found redirect
        URI redirectUri;
        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            redirectUri = URI.create(targetUrl);
        } else {
            String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl.trim() : "http://localhost:3000";
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String path = targetUrl.startsWith("/") ? targetUrl : "/" + targetUrl;
            redirectUri = URI.create(base + path);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirectUri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Returns metadata for a short code.
     */
    @GetMapping("/api/shorten/{shortCode}")
    public ResponseEntity<ShortUrlResponse> getShortCodeMetadata(@PathVariable String shortCode) {
        ShortUrl mapping = shortUrlService.getByShortCode(shortCode)
                .orElseThrow(() -> new InvalidTransferException("Short link not found: " + shortCode));

        return ResponseEntity.ok(toResponse(mapping));
    }

    /**
     * Secure receipt viewer endpoint: retrieves transaction details with masked account numbers
     * while enforcing customer ownership checks (User A cannot view User B's receipt).
     */
    @GetMapping("/api/receipts/{transactionId}")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @PathVariable String transactionId,
            HttpServletRequest httpRequest) {
        Long customerId = extractCustomerIdFromRequest(httpRequest);
        Transaction txn = shortUrlService.getAuthorizedReceipt(transactionId, customerId);

        Optional<ShortUrl> mapping = shortUrlService.getByTransactionId(transactionId);
        String shortCode = mapping.map(ShortUrl::getShortCode).orElse(null);
        String shortUrl = shortCode != null ? shortUrlService.buildFullShortUrl(shortCode) : null;

        ReceiptResponse resp = new ReceiptResponse(
                txn.getTransactionId(),
                maskAccount(txn.getSourceAccount()),
                maskAccount(txn.getDestinationAccount()),
                txn.getAmount(),
                txn.getType(),
                txn.getStatus(),
                txn.getDescription(),
                txn.getTimestamp(),
                shortCode,
                shortUrl
        );

        return ResponseEntity.ok(resp);
    }

    private Long extractCustomerIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return jwtService.extractCustomerId(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private ShortUrlResponse toResponse(ShortUrl entity) {
        return new ShortUrlResponse(
                entity.getShortCode(),
                shortUrlService.buildFullShortUrl(entity.getShortCode()),
                entity.getTargetUrl(),
                entity.getTransactionId(),
                entity.getExpiresAt(),
                entity.getClickCount()
        );
    }

    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) {
            return "****";
        }
        return account.substring(0, 4) + "-***" + account.charAt(account.length() - 1);
    }
}
