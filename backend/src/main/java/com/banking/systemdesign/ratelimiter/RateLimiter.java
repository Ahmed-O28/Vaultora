package com.banking.systemdesign.ratelimiter;

/**
 * Component 1: Rate Limiter
 * 
 * Responsibility: Protect sensitive banking endpoints (e.g., login, transfers, bill payments)
 * from abuse, brute force attacks, and denial-of-service spikes.
 * 
 * Future Production Implementation:
 * A distributed Redis token-bucket or sliding-window log to enforce rate limits
 * across multiple backend instances.
 */
public interface RateLimiter {

    /**
     * Checks if a request identified by the given key (e.g., client IP or account ID) is allowed.
     *
     * @param key unique identifier of client or action
     * @return true if request is permitted, false if rate limited
     */
    boolean allowRequest(String key);

    /**
     * Resets rate limit statistics for the given key (useful for testing).
     */
    void reset(String key);
}
