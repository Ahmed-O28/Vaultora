package com.banking.controller;

import com.banking.systemdesign.sharding.ConsistentHashRouter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/sharding")
@CrossOrigin(origins = "*")
public class ShardingController {

    private final ConsistentHashRouter router;

    public ShardingController(ConsistentHashRouter router) {
        this.router = router;
    }

    @GetMapping("/route")
    public ResponseEntity<Map<String, Object>> routeKey(@RequestParam String key) {
        int shardId = router.getShard(key);
        String shardName = router.getShardName(shardId);
        long hashValue = router.hash(key.trim().toLowerCase());
        return ResponseEntity.ok(Map.of(
                "key", key,
                "hashValue", hashValue,
                "assignedShardId", shardId,
                "assignedShardName", shardName
        ));
    }

    @GetMapping("/ring")
    public ResponseEntity<Map<String, Object>> getRing() {
        return ResponseEntity.ok(Map.of(
                "shards", router.getPhysicalShards(),
                "totalVirtualNodes", router.getRingTopology().size(),
                "ring", router.getRingTopology()
        ));
    }
}
