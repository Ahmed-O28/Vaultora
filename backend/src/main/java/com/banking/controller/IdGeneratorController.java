package com.banking.controller;

import com.banking.systemdesign.idgenerator.DistributedSnowflakeIdGenerator;
import com.banking.systemdesign.idgenerator.IdInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the Unique ID Generator so that its output (transaction ids "TXN-...", payment ids "BILL-...")
 * can be inspected and verified.
 */
@RestController
@RequestMapping("/api/system/ids")
@CrossOrigin(origins = "*")
public class IdGeneratorController {

    private final DistributedSnowflakeIdGenerator generator;

    public IdGeneratorController(DistributedSnowflakeIdGenerator generator) {
        this.generator = generator;
    }

    /** GET /api/system/ids/generate?prefix=TXN&count=3 */
    @GetMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam(name = "prefix", defaultValue = "TXN") String prefix,
            @RequestParam(name = "count", defaultValue = "1") int count) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (count < 1 || count > 100) {
            body.put("error", "count must be between 1 and 100");
            return ResponseEntity.badRequest().body(body);
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(generator.generateId(prefix));
        }
        body.put("prefix", DistributedSnowflakeIdGenerator.sanitizePrefix(prefix));
        body.put("count", ids.size());
        body.put("ids", ids);
        return ResponseEntity.ok(body);
    }

    /** GET /api/system/ids/parse?id=TXN-7254849302817280001 */
    @GetMapping("/parse")
    public ResponseEntity<?> parse(@RequestParam("id") String id) {
        try {
            IdInfo info = DistributedSnowflakeIdGenerator.parse(id);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}
