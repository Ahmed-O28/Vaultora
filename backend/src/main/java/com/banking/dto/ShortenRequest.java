package com.banking.dto;

public class ShortenRequest {

    private String url;
    private String transactionId;
    private Long expiresInSeconds;

    public ShortenRequest() {
    }

    public ShortenRequest(String url) {
        this.url = url;
    }

    public ShortenRequest(String url, String transactionId, Long expiresInSeconds) {
        this.url = url;
        this.transactionId = transactionId;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getOriginalUrl() {
        return url;
    }

    public void setOriginalUrl(String originalUrl) {
        if (this.url == null || this.url.isBlank()) {
            this.url = originalUrl;
        }
    }

    public Long getTtlHours() {
        return expiresInSeconds != null ? expiresInSeconds / 3600 : null;
    }

    public void setTtlHours(Long ttlHours) {
        if (ttlHours != null && this.expiresInSeconds == null) {
            this.expiresInSeconds = ttlHours * 3600;
        }
    }
}
