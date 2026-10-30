package com.banking.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A push notification delivered to a customer (transfer debit/credit alerts, bill-payment receipts, ...).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** TRANSFER_DEBIT, TRANSFER_CREDIT, BILL_PAYMENT, SYSTEM */
    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** Transaction id (TXN-...) this notification refers to, when applicable. */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "seen", nullable = false)
    private boolean seen = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Notification() {
    }

    public Notification(Long customerId, String type, String title, String message, String referenceId) {
        this.customerId = customerId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceId = referenceId;
        this.seen = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
