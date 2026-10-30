package com.banking.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects runtime performance evaluation metrics for System Design evaluation.
 * Tracks transfer throughput, success/error rates, rate limiter rejections,
 * cache hit/miss ratios, and shard distribution.
 */
@Service
public class MetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<Integer, AtomicLong> shardAccessCounts = new ConcurrentHashMap<>();

    public MetricsService() {
        shardAccessCounts.put(1, new AtomicLong(0));
        shardAccessCounts.put(2, new AtomicLong(0));
        shardAccessCounts.put(3, new AtomicLong(0));
        shardAccessCounts.put(4, new AtomicLong(0));
    }

    public void recordRequest(boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    public void recordRateLimitRejection() {
        rateLimitedRequests.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordShardAccess(int shardId) {
        shardAccessCounts.computeIfAbsent(shardId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("totalRequests", totalRequests.get());
        snapshot.put("successfulRequests", successfulRequests.get());
        snapshot.put("failedRequests", failedRequests.get());
        snapshot.put("rateLimitedRequests", rateLimitedRequests.get());
        snapshot.put("cacheHits", cacheHits.get());
        snapshot.put("cacheMisses", cacheMisses.get());

        Map<String, Long> shardDistribution = new HashMap<>();
        shardAccessCounts.forEach((shard, count) -> shardDistribution.put("shard_" + shard, count.get()));
        snapshot.put("shardDistribution", shardDistribution);

        return snapshot;
    }
}
