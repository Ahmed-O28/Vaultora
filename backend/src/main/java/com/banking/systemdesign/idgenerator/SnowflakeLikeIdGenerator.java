package com.banking.systemdesign.idgenerator;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trivial baseline implementation of UniqueIdGenerator based on timestamp + sequence counter.
 * Ensures monotonicity and uniqueness within the current node.
 */
@Component
public class SnowflakeLikeIdGenerator implements UniqueIdGenerator {

    private final AtomicInteger sequence = new AtomicInteger(0);
    private final int nodeId;

    public SnowflakeLikeIdGenerator() {
        this(1); // Node ID 1 by default
    }

    public SnowflakeLikeIdGenerator(int nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String generateId(String prefix) {
        long timestamp = Instant.now().toEpochMilli();
        int seq = sequence.incrementAndGet() % 10000;
        String cleanPrefix = (prefix == null || prefix.isBlank()) ? "ID" : prefix.toUpperCase();
        return String.format("%s-%d-N%d-%04d", cleanPrefix, timestamp, nodeId, seq);
    }
}
