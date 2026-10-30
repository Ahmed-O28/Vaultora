package com.banking.dto;

import java.time.Instant;

public class CustomerResponse {

    private Long customerId;
    private String fullName;
    private String email;
    private Instant createdAt;
    private String token;

    public CustomerResponse() {
    }

    public CustomerResponse(Long customerId, String fullName, String email, Instant createdAt) {
        this(customerId, fullName, email, createdAt, null);
    }

    public CustomerResponse(Long customerId, String fullName, String email, Instant createdAt, String token) {
        this.customerId = customerId;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
        this.token = token;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
