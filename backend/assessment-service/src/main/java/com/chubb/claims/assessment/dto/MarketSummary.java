package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Claim counts for one market. */
public record MarketSummary(
        @Schema(description = "ISO country code", example = "SG")
        String market,
        long totalClaims,
        @Schema(description = "SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED")
        long pendingClaims,
        long approvedClaims,
        long rejectedClaims,
        long settledClaims) {
}
