package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Claim counts for one claim type. */
public record ClaimTypeSummary(
        ClaimType claimType,
        long totalClaims,
        @Schema(description = "SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED")
        long pendingClaims,
        long approvedClaims,
        long rejectedClaims,
        long settledClaims) {
}
