package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A claim as shown in the claims staff's work queue. */
public record ClaimSummaryResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        String claimType,
        String market,
        ClaimStatus status,
        BigDecimal claimAmount,
        String currency,
        LocalDate incidentDate,
        Long assignedStaffId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimSummaryResponse from(RemoteClaim claim) {
        return new ClaimSummaryResponse(
                claim.id(),
                claim.claimNumber(),
                claim.policyNumber(),
                claim.claimType(),
                claim.market(),
                claim.status(),
                claim.claimAmount(),
                claim.currency(),
                claim.incidentDate(),
                claim.assignedStaffId(),
                claim.createdAt(),
                claim.updatedAt());
    }
}
