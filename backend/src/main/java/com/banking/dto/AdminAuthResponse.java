package com.banking.dto;

import java.time.Instant;

public class AdminAuthResponse {

    private Long adminId;
    private String username;
    private String email;
    private String role;
    private Instant createdAt;
    private String token;

    public AdminAuthResponse() {
    }

    public AdminAuthResponse(Long adminId, String username, String email, String role, Instant createdAt, String token) {
        this.adminId = adminId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.token = token;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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
