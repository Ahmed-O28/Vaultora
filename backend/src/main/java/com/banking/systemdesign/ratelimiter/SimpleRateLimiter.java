package com.banking.systemdesign.ratelimiter;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trivial in-memory baseline implementation of RateLimiter.
 * Uses a simple per-key fixed-window counter (e.g., maximum 10 requests per minute).
 */
@Component
public class SimpleRateLimiter implements RateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowDurationMillis;
    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

    public SimpleRateLimiter() {
        this(10, 60_000); // 10 requests per 60 seconds by default
    }

    public SimpleRateLimiter(int maxRequestsPerWindow, long windowDurationMillis) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowDurationMillis = windowDurationMillis;
    }

    @Override
    public synchronized boolean allowRequest(String key) {
        long now = Instant.now().toEpochMilli();
        RequestCounter counter = requestCounts.computeIfAbsent(key, k -> new RequestCounter(now, 0));

        // If window expired, reset counter
        if (now - counter.windowStartMillis > windowDurationMillis) {
            counter.windowStartMillis = now;
            counter.count = 0;
        }

        if (counter.count < maxRequestsPerWindow) {
            counter.count++;
            return true;
        }
        return false;
    }

    @Override
    public void reset(String key) {
        requestCounts.remove(key);
    }

    private static class RequestCounter {
        long windowStartMillis;
        int count;

        RequestCounter(long windowStartMillis, int count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
