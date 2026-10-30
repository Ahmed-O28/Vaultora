package com.banking.controller;

import com.banking.dto.BeneficiaryRequest;
import com.banking.dto.CustomerResponse;
import com.banking.dto.LoginRequest;
import com.banking.dto.RegisterRequest;
import com.banking.model.Account;
import com.banking.model.Beneficiary;
import com.banking.model.Customer;
import com.banking.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    private final CustomerService customerService;
    private final com.banking.service.JwtService jwtService;

    public CustomerController(CustomerService customerService, com.banking.service.JwtService jwtService) {
        this.customerService = customerService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest request) {
        Customer customer = customerService.register(request);
        String token = jwtService.generateToken(customer.getCustomerId(), customer.getEmail());
        CustomerResponse response = new CustomerResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getCreatedAt(),
                token
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerResponse> login(@Valid @RequestBody LoginRequest request) {
        Customer customer = customerService.authenticate(request);
        String token = jwtService.generateToken(customer.getCustomerId(), customer.getEmail());
        CustomerResponse response = new CustomerResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getCreatedAt(),
                token
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{customerId}/accounts")
    public ResponseEntity<List<Account>> getCustomerAccounts(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.getCustomerAccounts(customerId));
    }

    @PostMapping("/{customerId}/beneficiaries")
    public ResponseEntity<Beneficiary> addBeneficiary(@PathVariable Long customerId,
                                                      @Valid @RequestBody BeneficiaryRequest request) {
        Beneficiary beneficiary = customerService.addBeneficiary(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiary);
    }

    @GetMapping("/{customerId}/beneficiaries")
    public ResponseEntity<List<Beneficiary>> getBeneficiaries(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.getCustomerBeneficiaries(customerId));
    }

    @PostMapping("/{customerId}/verify-mpin")
    public ResponseEntity<java.util.Map<String, Object>> verifyMpin(
            @PathVariable Long customerId,
            @Valid @RequestBody com.banking.dto.MpinRequest request) {
        customerService.verifyMpin(customerId, request.getMpin());

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("verified", true);
        response.put("message", "MPIN verified successfully");

        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            List<Account> accounts = customerService.getCustomerAccounts(customerId);
            accounts.stream()
                    .filter(a -> a.getAccountNumber().equalsIgnoreCase(request.getAccountNumber()))
                    .findFirst()
                    .ifPresent(acc -> {
                        response.put("accountNumber", acc.getAccountNumber());
                        response.put("balance", acc.getBalance());
                        response.put("customerName", acc.getCustomerName());
                    });
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{customerId}/set-mpin")
    public ResponseEntity<java.util.Map<String, Object>> setMpin(
            @PathVariable Long customerId,
            @RequestBody java.util.Map<String, String> body) {
        String newMpin = body.get("mpin");
        customerService.setMpin(customerId, newMpin);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", "MPIN updated successfully");
        return ResponseEntity.ok(response);
    }
}
