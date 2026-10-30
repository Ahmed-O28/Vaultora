package com.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Non-sensitive customer representation for the Admin Dashboard.
 * Strict Security: Never exposes passwordHash or mpinHash.
 */
public class AdminCustomerView {

    private Long customerId;
    private String fullName;
    private String email;
    private String accountNumber;
    private BigDecimal balance;
    private Integer shardId;
    private String status;
    private Instant createdAt;

    public AdminCustomerView() {
    }

    public AdminCustomerView(Long customerId, String fullName, String email, String accountNumber,
                             BigDecimal balance, Integer shardId, String status, Instant createdAt) {
        this.customerId = customerId;
        this.fullName = fullName;
        this.email = email;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.shardId = shardId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getShardId() {
        return shardId;
    }

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
