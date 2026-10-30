package com.banking;

import com.banking.systemdesign.sharding.ConsistentHashRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Consistent Hashing & Database Sharding Tests")
public class ConsistentHashRouterTest {

    private ConsistentHashRouter router;

    @BeforeEach
    void setUp() {
        router = new ConsistentHashRouter(5);
    }

    @Test
    @DisplayName("Hash routing is deterministic for same key")
    void testDeterminism() {
        int shard1 = router.getShard("alice@bank.local");
        int shard2 = router.getShard("alice@bank.local");
        assertEquals(shard1, shard2, "Same email key must consistently resolve to the same shard");
    }

    @Test
    @DisplayName("Keys are distributed across four database shards")
    void testKeyDistribution() {
        String[] emails = {
                "alice@bank.local",
                "bob@bank.local",
                "charlie@bank.local",
                "diana@bank.local",
                "edward@bank.local",
                "fiona@bank.local",
                "george@bank.local",
                "helen@bank.local",
                "ivan@bank.local",
                "jack@bank.local"
        };

        Set<Integer> observedShards = new HashSet<>();

        for (String email : emails) {
            int shard = router.getShard(email);
            assertTrue(shard >= 1 && shard <= 4, "Shard ID must be between 1 and 4");
            observedShards.add(shard);
        }

        assertTrue(observedShards.size() >= 3, "Consistent hashing should distribute keys across multiple shards");
    }

    @Test
    @DisplayName("Empty or null key safely falls back to default Shard 1")
    void testNullOrEmptyKey() {
        assertEquals(1, router.getShard(null));
        assertEquals(1, router.getShard(""));
        assertEquals(1, router.getShard("   "));
    }

    @Test
    @DisplayName("Can add and remove shards dynamically")
    void testAddRemoveShard() {
        assertEquals(4, router.getPhysicalShards().size());

        router.addShard(5, "PostgreSQL-Shard-5");
        assertEquals(5, router.getPhysicalShards().size());
        assertEquals("PostgreSQL-Shard-5", router.getShardName(5));

        router.removeShard(5);
        assertEquals(4, router.getPhysicalShards().size());
        assertFalse(router.getPhysicalShards().containsKey(5));
    }

    @Test
    @DisplayName("Ring topology exposes virtual nodes properly for 4 shards")
    void testRingTopology() {
        var topology = router.getRingTopology();
        // 4 physical shards * 5 virtual nodes = 20 entries on ring
        assertEquals(20, topology.size());

        for (Map<String, Object> entry : topology) {
            assertNotNull(entry.get("hash"));
            assertNotNull(entry.get("shardId"));
            assertNotNull(entry.get("virtualNode"));
        }
    }

    @Test
    @DisplayName("ShardRouter interface integration works as primary routing contract")
    void testShardRouterInterface() {
        assertEquals(4, router.getShardCount());
        int s1 = router.resolveShard("alice@bank.local");
        int s2 = router.getShard("alice@bank.local");
        assertEquals(s1, s2);
        assertTrue(s1 >= 1 && s1 <= 4);
    }
}
