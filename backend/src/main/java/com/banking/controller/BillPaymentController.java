package com.banking.controller;

import com.banking.dto.BillPaymentRequest;
import com.banking.model.BillPayment;
import com.banking.service.BillPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
@CrossOrigin(origins = "*")
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    public BillPaymentController(BillPaymentService billPaymentService) {
        this.billPaymentService = billPaymentService;
    }

    @PostMapping("/pay")
    public ResponseEntity<BillPayment> payBill(@Valid @RequestBody BillPaymentRequest request) {
        BillPayment payment = billPaymentService.payBill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<BillPayment>> getBillPayments(@PathVariable String accountNumber) {
        return ResponseEntity.ok(billPaymentService.getBillPaymentsByAccount(accountNumber));
    }
}
