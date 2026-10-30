package com.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Entity representing a persistent shortened URL mapping.
 * Used for compact transaction receipt links, verification URLs, and payment links.
 */
@Entity
@Table(name = "short_urls", indexes = {
        @Index(name = "idx_short_urls_code", columnList = "short_code", unique = true),
        @Index(name = "idx_short_urls_txn", columnList = "transaction_id")
})
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @NotBlank
    @Column(name = "target_url", nullable = false, length = 1024)
    private String targetUrl;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "owner_customer_id")
    private Long ownerCustomerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    public ShortUrl() {
    }

    public ShortUrl(String shortCode, String targetUrl, String transactionId, Long ownerCustomerId, Instant expiresAt) {
        this.shortCode = shortCode;
        this.targetUrl = targetUrl;
        this.transactionId = transactionId;
        this.ownerCustomerId = ownerCustomerId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.clickCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Long getOwnerCustomerId() {
        return ownerCustomerId;
    }

    public void setOwnerCustomerId(Long ownerCustomerId) {
        this.ownerCustomerId = ownerCustomerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
