package com.banking.systemdesign.sharding;

/**
 * Baseline legacy implementation of ShardRouter using hash-modulo partitioning.
 * Retired from active Spring routing in favor of ConsistentHashRouter with 4 database shards.
 * Retained for backward compatibility and comparison tests.
 */
public class ModuloShardRouter implements ShardRouter {

    private final int shardCount;

    public ModuloShardRouter() {
        this(2); // 2 PostgreSQL shards as per university project requirements
    }

    public ModuloShardRouter(int shardCount) {
        this.shardCount = shardCount;
    }

    @Override
    public int resolveShard(String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            return 1;
        }
        // Consistent positive modulo across shardCount
        int hash = Math.abs(routingKey.hashCode());
        return (hash % shardCount) + 1;
    }

    @Override
    public int getShardCount() {
        return shardCount;
    }
}
