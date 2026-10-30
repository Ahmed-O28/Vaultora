package com.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "mpin_hash")
    private String mpinHash;

    @Column(name = "shard_id")
    private Integer shardId = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Customer() {
    }

    public Customer(String fullName, String email, String passwordHash) {
        this(fullName, email, passwordHash, null);
    }

    public Customer(String fullName, String email, String passwordHash, String mpinHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.mpinHash = mpinHash;
        this.createdAt = Instant.now();
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getMpinHash() {
        return mpinHash;
    }

    public void setMpinHash(String mpinHash) {
        this.mpinHash = mpinHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getShardId() {
        return shardId;
    }

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }
}
