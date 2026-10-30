package com.banking.controller;

import com.banking.dto.AutocompleteSuggestion;
import com.banking.service.BankingAutocompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class AutocompleteController {

    private final BankingAutocompleteService autocompleteService;

    public AutocompleteController(BankingAutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    /**
     * Autocomplete suggestions for an authenticated customer's beneficiaries.
     * Protected by JwtAuthInterceptor (strict customer isolation).
     */
    @GetMapping("/api/customers/{customerId}/autocomplete/beneficiaries")
    public ResponseEntity<List<AutocompleteSuggestion>> getBeneficiarySuggestions(
            @PathVariable Long customerId,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "5") int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<AutocompleteSuggestion> suggestions = autocompleteService.suggestBeneficiaries(
                customerId, query.trim(), limit);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Autocomplete suggestions for supported billers.
     */
    @GetMapping("/api/autocomplete/billers")
    public ResponseEntity<List<AutocompleteSuggestion>> getBillerSuggestions(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "5") int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<AutocompleteSuggestion> suggestions = autocompleteService.suggestBillers(query.trim(), limit);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Combined autocomplete suggestions across beneficiaries and billers for the authenticated customer.
     */
    @GetMapping("/api/customers/{customerId}/autocomplete")
    public ResponseEntity<List<AutocompleteSuggestion>> getCombinedSuggestions(
            @PathVariable Long customerId,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "6") int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        int halfLimit = Math.max(2, limit / 2);
        List<AutocompleteSuggestion> beneficiaries = autocompleteService.suggestBeneficiaries(customerId, query.trim(), halfLimit);
        List<AutocompleteSuggestion> billers = autocompleteService.suggestBillers(query.trim(), halfLimit);

        List<AutocompleteSuggestion> combined = new ArrayList<>(beneficiaries);
        combined.addAll(billers);

        return ResponseEntity.ok(combined);
    }
}
