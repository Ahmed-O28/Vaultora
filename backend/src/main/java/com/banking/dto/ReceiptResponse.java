package com.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safe receipt response DTO that masks account numbers and avoids leaking
 * sensitive banking secrets.
 */
public class ReceiptResponse {

    private String transactionId;
    private String maskedSourceAccount;
    private String maskedDestinationAccount;
    private BigDecimal amount;
    private String type;
    private String status;
    private String description;
    private Instant timestamp;
    private String shortCode;
    private String shortUrl;

    public ReceiptResponse() {
    }

    public ReceiptResponse(String transactionId, String maskedSourceAccount, String maskedDestinationAccount,
                           BigDecimal amount, String type, String status, String description,
                           Instant timestamp, String shortCode, String shortUrl) {
        this.transactionId = transactionId;
        this.maskedSourceAccount = maskedSourceAccount;
        this.maskedDestinationAccount = maskedDestinationAccount;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.description = description;
        this.timestamp = timestamp;
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getMaskedSourceAccount() {
        return maskedSourceAccount;
    }

    public void setMaskedSourceAccount(String maskedSourceAccount) {
        this.maskedSourceAccount = maskedSourceAccount;
    }

    public String getMaskedDestinationAccount() {
        return maskedDestinationAccount;
    }

    public void setMaskedDestinationAccount(String maskedDestinationAccount) {
        this.maskedDestinationAccount = maskedDestinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }
}
