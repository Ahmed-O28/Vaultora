package com.banking.systemdesign.consistenthashing;

import java.util.Collection;

/**
 * Component 3: Consistent Hashing Router
 * 
 * Responsibility: Assign keys to server nodes on a virtual hash ring to minimize key
 * reassignments when nodes/shards join or leave the cluster.
 * 
 * Future Production Implementation:
 * A ring with MD5/MurmurHash and configurable virtual nodes (e.g. 100 virtual nodes per server)
 * to ensure uniform load distribution across dynamic clusters.
 */
public interface ConsistentHashRouter {

    /**
     * Finds the target server or shard node responsible for the given key.
     *
     * @param key the routing key (e.g. account number or cache key)
     * @return the assigned node identifier (e.g. "shard-1", "shard-2")
     */
    String getNode(String key);

    /**
     * Dynamically adds a new node to the hash ring.
     */
    void addNode(String node);

    /**
     * Removes an existing node from the hash ring.
     */
    void removeNode(String node);

    /**
     * Returns the list of all currently registered nodes on the ring.
     */
    Collection<String> getNodes();
}
