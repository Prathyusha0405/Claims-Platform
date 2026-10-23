package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StatusSummaryResponse(
        @Schema(description = "Every claim, in any status (within the market filter, if one was given)")
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
        @Schema(description = "The market the figures above are limited to; null when they cover every market", nullable = true)
        String market,
        @Schema(description = "Counts for every market that has claims, by country code. Never narrowed by the market filter, "
                + "so one market can be read against the rest")
        List<MarketSummary> byMarket,
        Instant generatedAt) {
}
