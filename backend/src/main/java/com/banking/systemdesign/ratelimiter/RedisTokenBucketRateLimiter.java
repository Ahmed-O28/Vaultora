package com.banking.systemdesign.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed Redis-backed Token Bucket Rate Limiter.
 *
 * Uses a Lua script for atomic token consumption across multiple app instances.
 * Falls back to the in-memory SimpleRateLimiter when Redis is unavailable
 * (fail-open
 * for non-critical endpoints, configurable per use case).
 *
 * Key strategy: "rl:{endpoint}:{identifier}" where identifier is the
 * authenticated
 * customer ID or client IP for unauthenticated endpoints.
 */
@Component
@Primary
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final SimpleRateLimiter fallback;
    private final int capacity;
    private final int refillRate;
    private final long refillIntervalMs;

    // Metrics
    private final AtomicLong allowedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong totalDecisionTimeNanos = new AtomicLong(0);
    private final AtomicLong decisionCount = new AtomicLong(0);

    /**
     * Lua script for atomic token bucket operations. This script:
     * 1. Reads current tokens and last refill time from Redis
     * 2. Calculates tokens to add based on elapsed time
     * 3. Attempts to consume one token
     * 4. Updates the bucket state atomically
     * Returns: remaining tokens (>= 0 if allowed, -1 if rejected)
     */
    private static final String TOKEN_BUCKET_LUA = "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local refillRate = tonumber(ARGV[2])\n" +
            "local refillIntervalMs = tonumber(ARGV[3])\n" +
            "local now = tonumber(ARGV[4])\n" +
            "\n" +
            "local bucket = redis.call('hmget', key, 'tokens', 'lastRefill')\n" +
            "local tokens = tonumber(bucket[1])\n" +
            "local lastRefill = tonumber(bucket[2])\n" +
            "\n" +
            "-- Initialize bucket if new\n" +
            "if tokens == nil then\n" +
            "  tokens = capacity\n" +
            "  lastRefill = now\n" +
            "end\n" +
            "\n" +
            "-- Refill tokens based on elapsed time\n" +
            "local elapsed = now - lastRefill\n" +
            "if elapsed > 0 then\n" +
            "  local intervalsElapsed = math.floor(elapsed / refillIntervalMs)\n" +
            "  if intervalsElapsed > 0 then\n" +
            "    tokens = math.min(capacity, tokens + (intervalsElapsed * refillRate))\n" +
            "    lastRefill = lastRefill + (intervalsElapsed * refillIntervalMs)\n" +
            "  end\n" +
            "end\n" +
            "\n" +
            "-- Try to consume a token\n" +
            "if tokens > 0 then\n" +
            "  tokens = tokens - 1\n" +
            "  redis.call('hmset', key, 'tokens', tokens, 'lastRefill', lastRefill)\n" +
            "  redis.call('expire', key, math.ceil(capacity / refillRate * refillIntervalMs / 1000) + 60)\n" +
            "  return tokens\n" +
            "else\n" +
            "  redis.call('hmset', key, 'tokens', tokens, 'lastRefill', lastRefill)\n" +
            "  redis.call('expire', key, math.ceil(capacity / refillRate * refillIntervalMs / 1000) + 60)\n" +
            "  return -1\n" +
            "end";

    private final DefaultRedisScript<Long> script;

    public RedisTokenBucketRateLimiter(
            StringRedisTemplate redisTemplate,
            SimpleRateLimiter fallback,
            @Value("${ratelimiter.capacity:10}") int capacity,
            @Value("${ratelimiter.refill-rate:20}") int refillRate,
            @Value("${ratelimiter.refill-interval-ms:15000}") long refillIntervalMs) {
        this.redisTemplate = redisTemplate;
        this.fallback = fallback;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.refillIntervalMs = refillIntervalMs;

        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(TOKEN_BUCKET_LUA);
        this.script.setResultType(Long.class);
    }

    @Override
    public boolean allowRequest(String key) {
        long start = System.nanoTime();
        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList("rl:" + key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(refillIntervalMs),
                    String.valueOf(System.currentTimeMillis()));
            boolean allowed = result != null && result >= 0;
            if (allowed) {
                allowedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
            }
            return allowed;
        } catch (Exception e) {
            log.warn("Redis rate limiter unavailable, falling back to in-memory limiter: {}", e.getMessage());
            // Fail-open: use in-memory fallback when Redis is down
            boolean allowed = fallback.allowRequest(key);
            if (allowed) {
                allowedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
            }
            return allowed;
        } finally {
            totalDecisionTimeNanos.addAndGet(System.nanoTime() - start);
            decisionCount.incrementAndGet();
        }
    }

    @Override
    public void reset(String key) {
        try {
            redisTemplate.delete("rl:" + key);
        } catch (Exception e) {
            log.warn("Redis reset failed, resetting in-memory fallback: {}", e.getMessage());
        }
        fallback.reset(key);
    }

    public long getAllowedCount() {
        return allowedCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRefillRate() {
        return refillRate;
    }

    public long getRefillIntervalMs() {
        return refillIntervalMs;
    }

    public double getAverageDecisionLatencyMs() {
        long count = decisionCount.get();
        if (count == 0)
            return 0.0;
        return (totalDecisionTimeNanos.get() / (double) count) / 1_000_000.0;
    }

    public long getDecisionCount() {
        return decisionCount.get();
    }
}
