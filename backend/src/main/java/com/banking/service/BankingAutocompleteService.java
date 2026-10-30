package com.banking.service;

import com.banking.dto.AutocompleteSuggestion;
import com.banking.model.Beneficiary;
import com.banking.repository.BeneficiaryRepository;
import com.banking.systemdesign.autocomplete.BankingTrie;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Enterprise Autocomplete Service integrating in-memory Trie indexing
 * with Redis prefix caching, cache invalidation, and metrics recording.
 * Isolates customer suggestions to ensure User A cannot view User B's beneficiaries.
 */
@Service
public class BankingAutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(BankingAutocompleteService.class);

    private final BeneficiaryRepository beneficiaryRepository;
    private final MetricsService metricsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.autocomplete.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    // Standard verified billers
    private static final List<Map<String, String>> STANDARD_BILLERS = List.of(
            Map.of("id", "ELEC-001", "name", "City Electric Co.", "category", "Utilities"),
            Map.of("id", "WATER-001", "name", "Metro Water Authority", "category", "Utilities"),
            Map.of("id", "GAS-001", "name", "National Gas Ltd.", "category", "Utilities"),
            Map.of("id", "INTERNET-001", "name", "Fiber Internet Inc.", "category", "Internet"),
            Map.of("id", "INSURANCE-001", "name", "SafeGuard Insurance", "category", "Insurance"),
            Map.of("id", "PHONE-001", "name", "Mobile Network Corp.", "category", "Mobile")
    );

    public BankingAutocompleteService(
            BeneficiaryRepository beneficiaryRepository,
            MetricsService metricsService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.metricsService = metricsService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Suggests matching beneficiaries for the authenticated customer.
     * Uses Redis prefix caching with Trie computation on cache misses.
     */
    public List<AutocompleteSuggestion> suggestBeneficiaries(Long customerId, String prefix, int limit) {
        if (customerId == null || prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix.trim().toLowerCase();
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }

        int max = limit > 0 && limit <= 20 ? limit : 5;
        String cacheKey = String.format("ac:bene:%d:%s:%d", customerId, normalized, max);

        // 1. Try reading from Redis cache
        List<AutocompleteSuggestion> cached = getFromCache(cacheKey);
        if (cached != null) {
            metricsService.recordCacheHit();
            return cached;
        }

        metricsService.recordCacheMiss();

        // 2. Compute from real customer domain beneficiaries via Trie
        List<Beneficiary> customerBenes = beneficiaryRepository.findByCustomerId(customerId);
        BankingTrie trie = new BankingTrie();

        for (Beneficiary b : customerBenes) {
            String nickname = b.getNickname();
            String maskedAcc = maskAccount(b.getBeneficiaryAccountNumber());
            String display = nickname + " (" + maskedAcc + ")";

            AutocompleteSuggestion suggestion = new AutocompleteSuggestion(
                    nickname,
                    "BENEFICIARY",
                    maskedAcc,
                    display
            );

            // Index full nickname and individual tokens
            trie.insert(nickname, suggestion);
            String[] tokens = nickname.split("\\s+");
            if (tokens.length > 1) {
                for (String token : tokens) {
                    trie.insert(token, suggestion);
                }
            }
        }

        List<AutocompleteSuggestion> results = trie.searchPrefix(normalized, max);

        // 3. Cache in Redis
        putInCache(cacheKey, results);

        return results;
    }

    /**
     * Suggests matching verified billers.
     */
    public List<AutocompleteSuggestion> suggestBillers(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix.trim().toLowerCase();
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }

        int max = limit > 0 && limit <= 20 ? limit : 5;
        String cacheKey = String.format("ac:billers:%s:%d", normalized, max);

        // Try Redis cache
        List<AutocompleteSuggestion> cached = getFromCache(cacheKey);
        if (cached != null) {
            metricsService.recordCacheHit();
            return cached;
        }

        metricsService.recordCacheMiss();

        // Build Trie from standard billers
        BankingTrie trie = new BankingTrie();
        for (Map<String, String> biller : STANDARD_BILLERS) {
            String name = biller.get("name");
            String id = biller.get("id");
            AutocompleteSuggestion s = new AutocompleteSuggestion(
                    name,
                    "BILLER",
                    id,
                    name + " · " + id
            );
            trie.insert(name, s);
            String[] tokens = name.split("\\s+");
            for (String tok : tokens) {
                trie.insert(tok, s);
            }
        }
        List<AutocompleteSuggestion> results = trie.searchPrefix(normalized, max);

        putInCache(cacheKey, results);
        return results;
    }

    /**
     * Invalidates all cached beneficiary suggestions for a given customer.
     * Called when a customer registers or deletes a beneficiary.
     */
    public void invalidateCustomerBeneficiaryCache(Long customerId) {
        if (customerId == null || redisTemplate == null) return;
        try {
            String pattern = String.format("ac:bene:%d:*", customerId);
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated {} autocomplete cache entries for customer {}", keys.size(), customerId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate Redis autocomplete cache: {}", e.getMessage());
        }
    }

    private List<AutocompleteSuggestion> getFromCache(String key) {
        if (redisTemplate == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<AutocompleteSuggestion>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis autocomplete cache read failed: {}", e.getMessage());
        }
        return null;
    }

    private void putInCache(String key, List<AutocompleteSuggestion> results) {
        if (redisTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis autocomplete cache write failed: {}", e.getMessage());
        }
    }

    private String maskAccount(String acc) {
        if (acc == null || acc.length() <= 4) return "****";
        return acc.substring(0, 4) + "-***" + acc.charAt(acc.length() - 1);
    }
}
