package com.banking.controller;

import com.banking.dto.TransferRequest;
import com.banking.model.Transaction;
import com.banking.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@CrossOrigin(origins = "*")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<Transaction> transferFunds(@Valid @RequestBody TransferRequest request) {
        Transaction transaction = transferService.executeTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }
}
