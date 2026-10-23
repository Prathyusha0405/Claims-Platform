package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Map;

/** The claims submitted in one period, by the status they have reached now. */
public record TrendPeriod(
        @Schema(description = "First day of the period (UTC): a Monday for weeks, the 1st for months", example = "2026-08-03")
        LocalDate start,
        @Schema(description = "Claim count for every status, zero where there are none")
        Map<ClaimStatus, Long> claimsByStatus) {
}
