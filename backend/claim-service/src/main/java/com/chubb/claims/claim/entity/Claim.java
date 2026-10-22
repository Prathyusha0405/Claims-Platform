package com.chubb.claims.claim.entity;

import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @Column(name = "policy_number", nullable = false, length = 30)
    private String policyNumber;

    @Column(name = "claimant_id", nullable = false)
    private Long claimantId;

    /** Snapshot taken from the claimant's token at submission time; claim-service has no users table. */
    @Column(name = "claimant_name", length = 100)
    private String claimantName;

    @Column(name = "claimant_email", length = 254)
    private String claimantEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", length = 200)
    private String incidentLocation;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "claim_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Column(name = "assigned_officer_id")
    private Long assignedOfficerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: two concurrent changes to the same claim cannot both win. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Claim() {
        // required by JPA
    }

    public Claim(String claimNumber, String policyNumber, Long claimantId, String claimantName, String claimantEmail,
                 ClaimType claimType, LocalDate incidentDate, String incidentLocation, String description,
                 BigDecimal claimAmount, Instant now) {
        this.claimNumber = claimNumber;
        this.policyNumber = policyNumber;
        this.claimantId = claimantId;
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.claimType = claimType;
        this.incidentDate = incidentDate;
        this.incidentLocation = incidentLocation;
        this.description = description;
        this.claimAmount = claimAmount;
        this.status = ClaimStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Moves the claim to {@code next} if the lifecycle allows it, otherwise throws. */
    public void transitionTo(ClaimStatus next, Instant now) {
        status.requireTransitionTo(next);
        this.status = next;
        this.updatedAt = now;
    }

    /** Takes an unassigned claim into review: SUBMITTED -> UNDER_REVIEW, owned by {@code officerId}. */
    public void assignTo(Long officerId, Instant now) {
        if (status != ClaimStatus.SUBMITTED) {
            throw new InvalidStatusTransitionException(
                    "Only a SUBMITTED claim can be assigned to an officer (current status: " + status + ")");
        }
        transitionTo(ClaimStatus.UNDER_REVIEW, now);
        this.assignedOfficerId = officerId;
    }

    public Long getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public Long getClaimantId() {
        return claimantId;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getIncidentLocation() {
        return incidentLocation;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getClaimAmount() {
        return claimAmount;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Long getAssignedOfficerId() {
        return assignedOfficerId;
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
