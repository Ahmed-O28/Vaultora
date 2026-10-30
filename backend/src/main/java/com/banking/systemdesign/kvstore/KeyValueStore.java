package com.banking.systemdesign.kvstore;

/**
 * Component 4: Key-Value Store
 * 
 * Responsibility: Provide fast, temporary key-value caching (e.g. session tokens,
 * non-authoritative balances, rate-limiting counters) with TTL expiration.
 * PostgreSQL remains the authoritative source of truth.
 * 
 * Future Production Implementation:
 * Redis cluster caching layer with cache-aside and write-through policies.
 */
public interface KeyValueStore {

    /**
     * Stores a key-value pair with a time-to-live in seconds.
     */
    void put(String key, String value, long ttlSeconds);

    /**
     * Retrieves the value associated with the key, or null if missing/expired.
     */
    String get(String key);

    /**
     * Removes the key-value pair.
     */
    void delete(String key);

    /**
     * Checks if the key exists and is unexpired.
     */
    boolean exists(String key);
}
