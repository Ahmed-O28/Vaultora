package com.banking.controller;

import com.banking.dto.*;
import com.banking.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Admin login with username/email and password.
     * Public endpoint, does not require Bearer token.
     */
    @PostMapping("/login")
    public ResponseEntity<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminAuthResponse response = adminService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves aggregated system health and multi-shard statistics.
     * Protected by ADMIN role.
     */
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    /**
     * Retrieves customers across both PostgreSQL shards without exposing passwords/MPINs.
     * Supports search by name/email/account and shard filtering.
     * Protected by ADMIN role.
     */
    @GetMapping("/customers")
    public ResponseEntity<List<AdminCustomerView>> getAllCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer shardId) {
        return ResponseEntity.ok(adminService.getAllCustomers(search, shardId));
    }

    /**
     * Retrieves all transactions across both PostgreSQL shards.
     * Supports filtering by search term, transaction type, status, and shard.
     * Protected by ADMIN role.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<AdminTransactionView>> getAllTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer shardId) {
        return ResponseEntity.ok(adminService.getAllTransactions(search, type, status, shardId));
    }
}
