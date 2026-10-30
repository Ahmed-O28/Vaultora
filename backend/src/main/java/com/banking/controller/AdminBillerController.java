package com.banking.controller;

import com.banking.model.Biller;
import com.banking.service.BillerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/billers")
@CrossOrigin(origins = "*")
public class AdminBillerController {

    private final BillerService billerService;

    public AdminBillerController(BillerService billerService) {
        this.billerService = billerService;
    }

    /**
     * Lists all billers for admin inspection and management.
     */
    @GetMapping
    public ResponseEntity<List<Biller>> listBillers() {
        return ResponseEntity.ok(billerService.getAllBillers());
    }

    /**
     * Updates a biller's status to 'ACTIVE' or 'INACTIVE'.
     * Accepts: { "status": "ACTIVE" | "INACTIVE" }
     */
    @RequestMapping(value = "/{billerId}/status", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Biller> updateStatus(
            @PathVariable String billerId,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Biller updated = billerService.updateBillerStatus(billerId, status);
        return ResponseEntity.ok(updated);
    }

    /**
     * Convenience toggle endpoint to quickly switch status between ACTIVE and INACTIVE.
     */
    @PostMapping("/{billerId}/toggle")
    public ResponseEntity<Biller> toggleStatus(@PathVariable String billerId) {
        Biller existing = billerService.getBillerById(billerId)
                .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + billerId));
        String targetStatus = "ACTIVE".equalsIgnoreCase(existing.getStatus()) ? "INACTIVE" : "ACTIVE";
        Biller updated = billerService.updateBillerStatus(billerId, targetStatus);
        return ResponseEntity.ok(updated);
    }
}
