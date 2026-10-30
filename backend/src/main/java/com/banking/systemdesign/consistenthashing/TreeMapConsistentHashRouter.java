package com.banking.systemdesign.consistenthashing;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Trivial baseline implementation of Consistent Hashing using Java's NavigableMap (TreeMap).
 * Simulates a circular hash ring with virtual nodes.
 */
@Component
public class TreeMapConsistentHashRouter implements ConsistentHashRouter {

    private final int numberOfReplicas;
    private final SortedMap<Integer, String> circle = new TreeMap<>();
    private final Set<String> physicalNodes = new HashSet<>();

    public TreeMapConsistentHashRouter() {
        this(3, List.of("shard-1", "shard-2", "shard-3", "shard-4")); // 3 virtual nodes per physical node across 4 shards
    }

    public TreeMapConsistentHashRouter(int numberOfReplicas, Collection<String> initialNodes) {
        this.numberOfReplicas = numberOfReplicas;
        if (initialNodes != null) {
            for (String node : initialNodes) {
                addNode(node);
            }
        }
    }

    @Override
    public synchronized void addNode(String node) {
        physicalNodes.add(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            int hash = hash(node + "#" + i);
            circle.put(hash, node);
        }
    }

    @Override
    public synchronized void removeNode(String node) {
        physicalNodes.remove(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            int hash = hash(node + "#" + i);
            circle.remove(hash);
        }
    }

    @Override
    public synchronized String getNode(String key) {
        if (circle.isEmpty()) {
            return null;
        }
        int hash = hash(key);
        if (!circle.containsKey(hash)) {
            SortedMap<Integer, String> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }

    @Override
    public synchronized Collection<String> getNodes() {
        return Collections.unmodifiableSet(new HashSet<>(physicalNodes));
    }

    private int hash(String key) {
        // Simple hash function for demonstration
        return Math.abs(key.hashCode());
    }
}
