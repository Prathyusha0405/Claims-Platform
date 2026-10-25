package com.chubb.claims.claim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One immutable audit row per claim event. */
@Entity
@Table(name = "claim_history")
public class ClaimHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private ClaimEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 30)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 30)
    private ClaimStatus toStatus;

    @Column(name = "performed_by", updatable = false)
    private Long performedBy;

    @Column(name = "performed_by_role", updatable = false, length = 20)
    private String performedByRole;

    @Column(updatable = false, length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Id of the event that produced this row, so a duplicate delivery can be recognised; null for seeded rows. */
    @Column(name = "event_id", updatable = false, length = 36)
    private String eventId;

    protected ClaimHistory() {
        // required by JPA
    }

    public ClaimHistory(Long claimId, ClaimEventType eventType, ClaimStatus fromStatus, ClaimStatus toStatus,
                        Long performedBy, String performedByRole, String details, Instant createdAt) {
        this(claimId, eventType, fromStatus, toStatus, performedBy, performedByRole, details, createdAt, null);
    }

    public ClaimHistory(Long claimId, ClaimEventType eventType, ClaimStatus fromStatus, ClaimStatus toStatus,
                        Long performedBy, String performedByRole, String details, Instant createdAt, String eventId) {
        this.eventId = eventId;
        this.claimId = claimId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.performedBy = performedBy;
        this.performedByRole = performedByRole;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public ClaimEventType getEventType() {
        return eventType;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public Long getPerformedBy() {
        return performedBy;
    }

    public String getPerformedByRole() {
        return performedByRole;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getEventId() {
        return eventId;
    }
}
