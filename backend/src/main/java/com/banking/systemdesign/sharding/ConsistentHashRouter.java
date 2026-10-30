package com.banking.systemdesign.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Robust Consistent Hashing Router for Database Sharding.
 *
 * Places 4 database shards onto a circular hash ring with virtual nodes
 * to ensure balanced, uniform key distribution across database instances.
 * Implements ShardRouter as the primary active sharding strategy.
 */
@Component
@Primary
public class ConsistentHashRouter implements ShardRouter {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRouter.class);

    private static final int DEFAULT_VIRTUAL_NODES = 5;
    private final int virtualNodes;
    private final NavigableMap<Long, ShardNode> ring = new ConcurrentSkipListMap<>();
    private final Map<Integer, String> physicalShards = new LinkedHashMap<>();

    public record ShardNode(int shardId, String shardName, String virtualNodeName) {}

    public ConsistentHashRouter() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashRouter(int virtualNodes) {
        this.virtualNodes = virtualNodes > 0 ? virtualNodes : DEFAULT_VIRTUAL_NODES;
        // Register four PostgreSQL shards: Shard 1 (5432), Shard 2 (5433), Shard 3 (5434), Shard 4 (5435)
        addShard(1, "PostgreSQL-Shard-1");
        addShard(2, "PostgreSQL-Shard-2");
        addShard(3, "PostgreSQL-Shard-3");
        addShard(4, "PostgreSQL-Shard-4");
    }

    public synchronized void addShard(int shardId, String shardName) {
        physicalShards.put(shardId, shardName);
        for (int i = 0; i < virtualNodes; i++) {
            String vNodeName = String.format("%s#VN%d", shardName, i);
            long hash = hash(vNodeName);
            ring.put(hash, new ShardNode(shardId, shardName, vNodeName));
        }
        log.info("Registered Shard {} ('{}') with {} virtual nodes on hash ring", shardId, shardName, virtualNodes);
    }

    public synchronized void removeShard(int shardId) {
        String shardName = physicalShards.remove(shardId);
        if (shardName != null) {
            for (int i = 0; i < virtualNodes; i++) {
                String vNodeName = String.format("%s#VN%d", shardName, i);
                ring.remove(hash(vNodeName));
            }
        }
    }

    /**
     * Determines which database shard should store a given key (e.g. user email or account ID)
     * by locating the first virtual node clockwise along the hash ring.
     */
    public int getShard(String key) {
        if (key == null || key.isBlank() || ring.isEmpty()) {
            return 1; // Default fallback to Shard 1
        }

        long hash = hash(key.trim().toLowerCase());
        Map.Entry<Long, ShardNode> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            // Wrap around clockwise to the start of the ring
            entry = ring.firstEntry();
        }

        return entry != null ? entry.getValue().shardId() : 1;
    }

    @Override
    public int resolveShard(String routingKey) {
        return getShard(routingKey);
    }

    @Override
    public int getShardCount() {
        return physicalShards.size();
    }

    public String getShardName(int shardId) {
        return physicalShards.getOrDefault(shardId, "Shard-" + shardId);
    }

    public Map<Integer, String> getPhysicalShards() {
        return Collections.unmodifiableMap(physicalShards);
    }

    public List<Map<String, Object>> getRingTopology() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, ShardNode> entry : ring.entrySet()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("hash", entry.getKey());
            map.put("shardId", entry.getValue().shardId());
            map.put("shardName", entry.getValue().shardName());
            map.put("virtualNode", entry.getValue().virtualNodeName());
            list.add(map);
        }
        return list;
    }

    /**
     * 64-bit cryptographic hash function (MD5 truncated to 64-bit positive long)
     * providing uniform dispersion along the 2^64 ring space.
     */
    public long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (bytes[i] & 0xFF);
            }
            return h & 0x7FFFFFFFFFFFFFFFL; // Force positive value
        } catch (NoSuchAlgorithmException e) {
            return Math.abs((long) key.hashCode());
        }
    }
}
