package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A claim as shown in the officers' work queue. */
public record ClaimSummaryResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        String claimType,
        ClaimStatus status,
        BigDecimal claimAmount,
        LocalDate incidentDate,
        Long assignedOfficerId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimSummaryResponse from(RemoteClaim claim) {
        return new ClaimSummaryResponse(
                claim.id(),
                claim.claimNumber(),
                claim.policyNumber(),
                claim.claimType(),
                claim.status(),
                claim.claimAmount(),
                claim.incidentDate(),
                claim.assignedOfficerId(),
                claim.createdAt(),
                claim.updatedAt());
    }
}
