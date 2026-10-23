package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of a claim list (my claims, search results). */
public record ClaimSummaryResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        ClaimType claimType,
        Market market,
        ClaimStatus status,
        BigDecimal claimAmount,
        Currency currency,
        LocalDate incidentDate,
        Long assignedStaffId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimSummaryResponse from(Claim claim) {
        return new ClaimSummaryResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getPolicyNumber(),
                claim.getClaimType(),
                claim.getMarket(),
                claim.getStatus(),
                claim.getClaimAmount(),
                claim.getCurrency(),
                claim.getIncidentDate(),
                claim.getAssignedStaffId(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }
}
