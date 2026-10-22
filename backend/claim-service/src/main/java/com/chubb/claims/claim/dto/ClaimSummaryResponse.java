package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of a claim list (my claims, search results). */
public record ClaimSummaryResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        ClaimType claimType,
        ClaimStatus status,
        BigDecimal claimAmount,
        LocalDate incidentDate,
        Long assignedOfficerId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimSummaryResponse from(Claim claim) {
        return new ClaimSummaryResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getPolicyNumber(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getClaimAmount(),
                claim.getIncidentDate(),
                claim.getAssignedOfficerId(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }
}
