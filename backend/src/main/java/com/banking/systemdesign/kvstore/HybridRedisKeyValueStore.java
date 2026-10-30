package com.banking.systemdesign.kvstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baseline KeyValueStore implementation.
 * Attempts to delegate to Redis when available, with an internal in-memory fallback
 * so that unit tests and offline development never crash when Redis is not running.
 */
@Component
public class HybridRedisKeyValueStore implements KeyValueStore {

    private static final Logger log = LoggerFactory.getLogger(HybridRedisKeyValueStore.class);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, CacheItem> inMemoryFallback = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public HybridRedisKeyValueStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public HybridRedisKeyValueStore() {
        this(null);
    }

    @Override
    public void put(String key, String value, long ttlSeconds) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
                return;
            } catch (Exception e) {
                log.warn("Redis write failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
        inMemoryFallback.put(key, new CacheItem(value, expiresAt));
    }

    @Override
    public String get(String key) {
        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis read failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        CacheItem item = inMemoryFallback.get(key);
        if (item == null) {
            return null;
        }
        if (Instant.now().toEpochMilli() > item.expiresAtMillis) {
            inMemoryFallback.remove(key);
            return null;
        }
        return item.value;
    }

    @Override
    public void delete(String key) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {
            }
        }
        inMemoryFallback.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    private static class CacheItem {
        final String value;
        final long expiresAtMillis;

        CacheItem(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
