package com.banking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Represents a partner biller with an admin-managed status ('ACTIVE' or 'INACTIVE').
 * Persisted in the database across application restarts.
 */
@Entity
@Table(name = "billers")
public class Biller {

    @Id
    @Column(name = "biller_id", length = 50, nullable = false)
    private String billerId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "ACTIVE"; // ACTIVE or INACTIVE

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    public Biller() {
    }

    public Biller(String billerId, String name, String category, String status) {
        this.billerId = billerId;
        this.name = name;
        this.category = category;
        this.status = status != null ? status : "ACTIVE";
        this.lastUpdated = Instant.now();
    }

    public Biller(String billerId, String name, String category, String status, Instant lastUpdated) {
        this.billerId = billerId;
        this.name = name;
        this.category = category;
        this.status = status != null ? status : "ACTIVE";
        this.lastUpdated = lastUpdated != null ? lastUpdated : Instant.now();
    }

    public String getBillerId() {
        return billerId;
    }

    public void setBillerId(String billerId) {
        this.billerId = billerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
