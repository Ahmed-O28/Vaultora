package com.banking.systemdesign;

import com.banking.systemdesign.idgenerator.DistributedSnowflakeIdGenerator;
import com.banking.systemdesign.idgenerator.IdInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class UniqueIdGeneratorFeatureTest {

    @Test
    @DisplayName("IDs are unique when generated concurrently from many threads")
    void idsAreUniqueAcrossThreads() throws Exception {
        DistributedSnowflakeIdGenerator generator = new DistributedSnowflakeIdGenerator(3);
        int threads = 8;
        int perThread = 5000;
        Set<String> all = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    all.add(generator.generateId("TXN"));
                }
                done.countDown();
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "generation did not finish in time");
        pool.shutdownNow();
        assertEquals(threads * perThread, all.size(), "duplicate ids were generated");
    }

    @Test
    @DisplayName("IDs are strictly increasing (time ordered) and keep the PREFIX- format")
    void idsAreTimeOrdered() {
        DistributedSnowflakeIdGenerator generator = new DistributedSnowflakeIdGenerator(1);
        long previous = -1;
        for (int i = 0; i < 20000; i++) {
            long next = generator.nextId();
            assertTrue(next > previous, "ids must be strictly increasing");
            previous = next;
        }
        assertTrue(generator.generateId("txn").matches("TXN-\\d{10,19}"));
        assertTrue(generator.generateId("BILL").startsWith("BILL-"));
    }

    @Test
    @DisplayName("IDs can be decoded back into prefix, node and creation time")
    void idsCanBeParsed() {
        DistributedSnowflakeIdGenerator generator = new DistributedSnowflakeIdGenerator(7);
        String id = generator.generateId("PAY");
        IdInfo info = DistributedSnowflakeIdGenerator.parse(id);
        assertEquals("PAY", info.prefix());
        assertEquals(7, info.nodeId());
        assertTrue(Duration.between(info.createdAt(), Instant.now()).abs().toSeconds() < 10);
        assertThrows(IllegalArgumentException.class, () -> DistributedSnowflakeIdGenerator.parse("garbage"));
        assertThrows(IllegalArgumentException.class, () -> DistributedSnowflakeIdGenerator.parse("TXN-abc"));
    }

    @Test
    @DisplayName("Sequence exhaustion within one millisecond never produces duplicates")
    void sequenceExhaustionIsHandled() {
        AtomicLong calls = new AtomicLong();
        // time advances by 1 ms only every 3000 clock reads, forcing > 4096 ids into the same millisecond
        DistributedSnowflakeIdGenerator generator = new DistributedSnowflakeIdGenerator(2,
                () -> DistributedSnowflakeIdGenerator.CUSTOM_EPOCH + 1000 + calls.incrementAndGet() / 3000);
        Set<Long> ids = new java.util.HashSet<>();
        List<Long> ordered = new ArrayList<>();
        for (int i = 0; i < 30000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "duplicate id " + id);
            ordered.add(id);
        }
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i) > ordered.get(i - 1));
        }
    }

    @Test
    @DisplayName("A small clock rollback is waited out, a large one is rejected")
    void clockRollbackIsHandled() {
        long[] time = {DistributedSnowflakeIdGenerator.CUSTOM_EPOCH + 100_000};
        DistributedSnowflakeIdGenerator generator = new DistributedSnowflakeIdGenerator(1, () -> time[0]++);
        long first = generator.nextId();
        time[0] -= 5; // clock jumps back 5 ms
        long second = generator.nextId();
        assertTrue(second > first, "id after small rollback must still be greater");

        long[] frozen = {DistributedSnowflakeIdGenerator.CUSTOM_EPOCH + 100_000};
        DistributedSnowflakeIdGenerator strict = new DistributedSnowflakeIdGenerator(1, () -> frozen[0]);
        strict.nextId();
        frozen[0] -= 60_000; // 60 s backwards
        assertThrows(IllegalStateException.class, strict::nextId);
    }

    @Test
    @DisplayName("Prefix is sanitised and node id is validated")
    void prefixAndNodeValidation() {
        assertEquals("ID", DistributedSnowflakeIdGenerator.sanitizePrefix(null));
        assertEquals("ID", DistributedSnowflakeIdGenerator.sanitizePrefix("  "));
        assertEquals("TXN", DistributedSnowflakeIdGenerator.sanitizePrefix(" tx-n! "));
        assertEquals(8, DistributedSnowflakeIdGenerator.sanitizePrefix("ABCDEFGHIJKLMNOP").length());
        assertThrows(IllegalArgumentException.class, () -> new DistributedSnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new DistributedSnowflakeIdGenerator(1024));
    }
}
