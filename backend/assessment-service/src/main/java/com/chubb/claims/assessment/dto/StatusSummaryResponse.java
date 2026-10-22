package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

public record StatusSummaryResponse(
        @Schema(description = "Every claim, in any status")
        long totalClaims,
        @Schema(description = "Not yet decided: SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED")
        long pendingClaims,
        @Schema(description = "Claims in status APPROVED")
        long approvedClaims,
        @Schema(description = "Claims in status REJECTED")
        long rejectedClaims,
        @Schema(description = "Claims in status SETTLED; totalClaims = pending + approved + rejected + settled")
        long settledClaims,
        @Schema(description = "Claim count for every status, zero where there are none")
        Map<ClaimStatus, Long> claimsByStatus,
        Instant generatedAt) {
}
