package com.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction record for the Admin Dashboard across database shards.
 */
public class AdminTransactionView {

    private Long id;
    private String transactionId;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String type;
    private String status;
    private String description;
    private Instant timestamp;
    private Integer shardId;

    public AdminTransactionView() {
    }

    public AdminTransactionView(Long id, String transactionId, String sourceAccount, String destinationAccount,
                                BigDecimal amount, String type, String status, String description,
                                Instant timestamp, Integer shardId) {
        this.id = id;
        this.transactionId = transactionId;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.description = description;
        this.timestamp = timestamp;
        this.shardId = shardId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(String destinationAccount) {
        this.destinationAccount = destinationAccount;
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

    public Integer getShardId() {
        return shardId;
    }

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }
}
