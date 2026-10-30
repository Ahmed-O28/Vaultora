package com.banking.controller;

import com.banking.model.Biller;
import com.banking.service.BillerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billers")
@CrossOrigin(origins = "*")
public class BillerController {

    private final BillerService billerService;

    public BillerController(BillerService billerService) {
        this.billerService = billerService;
    }

    /**
     * Retrieves all billers with their real-time statuses.
     * Also returns a map with 'billers' array to ensure backward compatibility with UI components.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBillers() {
        List<Biller> list = billerService.getAllBillers();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", list.size());
        response.put("billers", list);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves only ACTIVE billers that are currently eligible for customer payments.
     */
    @GetMapping("/active")
    public ResponseEntity<List<Biller>> getActiveBillers() {
        return ResponseEntity.ok(billerService.getActiveBillers());
    }

    /**
     * Retrieves a single biller by ID.
     */
    @GetMapping("/{billerId}")
    public ResponseEntity<Biller> getBillerById(@PathVariable String billerId) {
        return billerService.getBillerById(billerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
