package com.chubb.claims.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A staff member's assessment of one claim: who took it, and (once made) their decision. The claim's own status is owned
 * by claim-service and deliberately not stored here.
 */
@Entity
@Table(name = "claim_assessments")
public class ClaimAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, unique = true, updatable = false)
    private Long claimId;

    @Column(name = "claim_number", nullable = false, length = 30)
    private String claimNumber;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** Snapshot from the staff member's token; claim-service stores only staff member ids. */
    @Column(name = "staff_name", length = 100)
    private String staffName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssessmentDecision decision;

    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ClaimAssessment() {
        // required by JPA
    }

    public ClaimAssessment(Long claimId, String claimNumber, Long staffId, String staffName, Instant assignedAt) {
        this.claimId = claimId;
        this.claimNumber = claimNumber;
        this.staffId = staffId;
        this.staffName = staffName;
        this.assignedAt = assignedAt;
        this.createdAt = assignedAt;
        this.updatedAt = assignedAt;
    }

    public void assignTo(Long staffId, String staffName, Instant now) {
        this.staffId = staffId;
        this.staffName = staffName;
        this.assignedAt = now;
        this.decision = null;
        this.decisionReason = null;
        this.decidedAt = null;
        this.updatedAt = now;
    }

    public void recordDecision(AssessmentDecision decision, String reason, Instant now) {
        this.decision = decision;
        this.decisionReason = reason;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Long getStaffId() {
        return staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public AssessmentDecision getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
