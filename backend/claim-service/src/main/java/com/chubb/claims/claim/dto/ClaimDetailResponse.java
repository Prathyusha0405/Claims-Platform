package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Full claim view for tracking: the claim itself plus its audit timeline (oldest first). */
public record ClaimDetailResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        Long claimantId,
        String claimantName,
        String claimantEmail,
        ClaimType claimType,
        Market market,
        ClaimStatus status,
        BigDecimal claimAmount,
        Currency currency,
        LocalDate incidentDate,
        String incidentLocation,
        String description,
        Long assignedStaffId,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Audit timeline, oldest first")
        List<ClaimHistoryResponse> history) {

    public static ClaimDetailResponse from(Claim claim, List<ClaimHistory> history) {
        return new ClaimDetailResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getPolicyNumber(),
                claim.getClaimantId(),
                claim.getClaimantName(),
                claim.getClaimantEmail(),
                claim.getClaimType(),
                claim.getMarket(),
                claim.getStatus(),
                claim.getClaimAmount(),
                claim.getCurrency(),
                claim.getIncidentDate(),
                claim.getIncidentLocation(),
                claim.getDescription(),
                claim.getAssignedStaffId(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                history.stream().map(ClaimHistoryResponse::from).toList());
    }
}
