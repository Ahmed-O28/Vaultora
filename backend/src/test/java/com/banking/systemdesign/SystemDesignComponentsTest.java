package com.banking.systemdesign;

import com.banking.systemdesign.autocomplete.AutocompleteService;
import com.banking.systemdesign.autocomplete.TrieAutocompleteService;
import com.banking.systemdesign.consistenthashing.ConsistentHashRouter;
import com.banking.systemdesign.consistenthashing.TreeMapConsistentHashRouter;
import com.banking.systemdesign.idgenerator.SnowflakeLikeIdGenerator;
import com.banking.systemdesign.idgenerator.UniqueIdGenerator;
import com.banking.systemdesign.kvstore.HybridRedisKeyValueStore;
import com.banking.systemdesign.kvstore.KeyValueStore;
import com.banking.systemdesign.ratelimiter.RateLimiter;
import com.banking.systemdesign.ratelimiter.SimpleRateLimiter;
import com.banking.systemdesign.sharding.ModuloShardRouter;
import com.banking.systemdesign.sharding.ShardRouter;
import com.banking.systemdesign.urlshortener.Base62ShortUrlService;
import com.banking.systemdesign.urlshortener.ShortUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemDesignComponentsTest {

    @Test
    @DisplayName("Component 1: RateLimiter should block requests exceeding quota")
    void testRateLimiter() {
        RateLimiter limiter = new SimpleRateLimiter(2, 5000);
        assertTrue(limiter.allowRequest("user123"));
        assertTrue(limiter.allowRequest("user123"));
        assertFalse(limiter.allowRequest("user123"), "Third request must be blocked");
    }

    @Test
    @DisplayName("Component 2: ShardRouter should map accounts using Modulo or Consistent Hashing")
    void testShardRouter() {
        ShardRouter router = new ModuloShardRouter(2);
        int shardA = router.resolveShard("ACC-1001");
        int shardB = router.resolveShard("ACC-2001");
        assertTrue(shardA == 1 || shardA == 2);
        assertTrue(shardB == 1 || shardB == 2);

        // Verify primary ConsistentHashRouter implements ShardRouter across 4 shards
        ShardRouter consistentRouter = new com.banking.systemdesign.sharding.ConsistentHashRouter();
        assertEquals(4, consistentRouter.getShardCount());
        int s = consistentRouter.resolveShard("ACC-1001");
        assertTrue(s >= 1 && s <= 4);
    }

    @Test
    @DisplayName("Component 3: ConsistentHashRouter should assign and rebalance nodes")
    void testConsistentHashRouter() {
        ConsistentHashRouter router = new TreeMapConsistentHashRouter(3, List.of("shard-1", "shard-2"));
        String node = router.getNode("ACC-1001");
        assertNotNull(node);
        assertTrue(node.equals("shard-1") || node.equals("shard-2"));
    }

    @Test
    @DisplayName("Component 4: KeyValueStore should store and retrieve data with fallback")
    void testKeyValueStore() {
        KeyValueStore kv = new HybridRedisKeyValueStore();
        kv.put("session_1", "active", 60);
        assertEquals("active", kv.get("session_1"));
        assertTrue(kv.exists("session_1"));
        kv.delete("session_1");
        assertNull(kv.get("session_1"));
    }

    @Test
    @DisplayName("Component 5: UniqueIdGenerator should generate unique, non-colliding IDs")
    void testUniqueIdGenerator() {
        UniqueIdGenerator generator = new SnowflakeLikeIdGenerator(1);
        String id1 = generator.generateId("TXN");
        String id2 = generator.generateId("TXN");
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("TXN-"));
    }

    @Test
    @DisplayName("Component 6: ShortUrlService should shorten and resolve URLs")
    void testShortUrlService() {
        ShortUrlService shortener = new Base62ShortUrlService();
        String originalUrl = "https://bank.local/receipts/txn-987654";
        String code = shortener.shorten(originalUrl);
        assertNotNull(code);
        assertEquals(originalUrl, shortener.resolve(code));
    }

    @Test
    @DisplayName("Component 7: Biller model should encapsulate persistent active/inactive status")
    void testBillerModel() {
        com.banking.model.Biller biller = new com.banking.model.Biller("ELEC-001", "City Electric", "Utilities", "ACTIVE");
        assertEquals("ACTIVE", biller.getStatus());
        biller.setStatus("INACTIVE");
        assertEquals("INACTIVE", biller.getStatus());
    }

    @Test
    @DisplayName("Component 8: AutocompleteService should match prefix in Trie")
    void testAutocompleteService() {
        AutocompleteService autocomplete = new TrieAutocompleteService();
        List<String> results = autocomplete.suggest("elec", 5);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).toLowerCase().startsWith("elec"));
    }
}
