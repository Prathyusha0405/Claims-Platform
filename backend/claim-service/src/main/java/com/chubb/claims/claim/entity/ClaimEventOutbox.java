package com.chubb.claims.claim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A claim event waiting to be sent to Kafka. It is saved in the same transaction as the claim change (so the event exists
 * exactly when the change does) and deleted by the relay once Kafka has acknowledged it.
 */
@Entity
@Table(name = "claim_event_outbox")
public class ClaimEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(nullable = false, updatable = false, length = 8000)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClaimEventOutbox() {
        // required by JPA
    }

    public ClaimEventOutbox(Long claimId, String eventId, String payload, Instant createdAt) {
        this.claimId = claimId;
        this.eventId = eventId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
