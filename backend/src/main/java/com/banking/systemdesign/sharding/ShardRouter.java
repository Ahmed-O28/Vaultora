package com.banking.systemdesign.sharding;

/**
 * Component 2: Database Shard Router
 * 
 * Responsibility: Map an account or customer key to a specific PostgreSQL database shard ID.
 * 
 * Future Production Implementation:
 * Dynamic lookup via a Shard Map table or range-based routing to support hundreds of physical shards.
 */
public interface ShardRouter {

    /**
     * Determines which database shard (e.g. 1 or 2) should store the data for the given key.
     *
     * @param routingKey account number, customer ID, or user identifier
     * @return 1-based shard index (e.g., 1 or 2)
     */
    int resolveShard(String routingKey);

    /**
     * Total number of shards configured.
     */
    int getShardCount();
}
