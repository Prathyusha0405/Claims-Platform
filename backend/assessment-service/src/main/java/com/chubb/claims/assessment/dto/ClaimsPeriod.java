package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Map;

/** The claims submitted in one week or month, by the status they have reached now. */
public record ClaimsPeriod(
        @Schema(description = "First day of the period (UTC): a Monday for weeks, the 1st for months", example = "2026-08-03")
        LocalDate start,
        @Schema(description = "Every claim submitted in the period")
        long totalClaims,
        @Schema(description = "Not yet decided: SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED")
        long pendingClaims,
        @Schema(description = "Decided: APPROVED + REJECTED + SETTLED; totalClaims = pending + decided")
        long decidedClaims,
        @Schema(description = "Claim count for every status, zero where there are none")
        Map<ClaimStatus, Long> claimsByStatus) {
}
