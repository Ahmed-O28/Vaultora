package com.banking.controller;

import com.banking.service.MetricsService;
import com.banking.systemdesign.ratelimiter.RateLimiter;
import com.banking.systemdesign.ratelimiter.RedisTokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MetricsService metricsService;
    private final RateLimiter rateLimiter;

    @Autowired
    public MetricsController(MetricsService metricsService, @Autowired(required = false) RateLimiter rateLimiter) {
        this.metricsService = metricsService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> response = new HashMap<>(metricsService.getMetricsSnapshot());
        if (rateLimiter instanceof RedisTokenBucketRateLimiter rtb) {
            Map<String, Object> rlMetrics = new HashMap<>();
            rlMetrics.put("allowed", rtb.getAllowedCount());
            rlMetrics.put("rejected", rtb.getRejectedCount());
            rlMetrics.put("decisionCount", rtb.getDecisionCount());
            rlMetrics.put("avgDecisionLatencyMs", rtb.getAverageDecisionLatencyMs());
            rlMetrics.put("capacity", rtb.getCapacity());
            rlMetrics.put("refillRate", rtb.getRefillRate());
            response.put("rateLimiter", rlMetrics);
        }
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
