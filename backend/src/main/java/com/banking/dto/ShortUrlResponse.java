package com.banking.dto;

import java.time.Instant;

public class ShortUrlResponse {

    private String shortCode;
    private String shortUrl;
    private String targetUrl;
    private String transactionId;
    private Instant expiresAt;
    private long clickCount;

    public ShortUrlResponse() {
    }

    public ShortUrlResponse(String shortCode, String shortUrl, String targetUrl, String transactionId, Instant expiresAt, long clickCount) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.targetUrl = targetUrl;
        this.transactionId = transactionId;
        this.expiresAt = expiresAt;
        this.clickCount = clickCount;
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
}
