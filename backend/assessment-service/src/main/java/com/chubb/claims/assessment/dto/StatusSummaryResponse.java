package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
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
        @Schema(description = "The claim type the figures are limited to; null when they cover every type", nullable = true)
        ClaimType claimType,
        @Schema(description = "Only claims submitted on or after this date (UTC) are counted; null when the range has no start", nullable = true, example = "2026-08-01")
        LocalDate fromDate,
        @Schema(description = "Only claims submitted on or before this date (UTC) are counted; null when the range has no end", nullable = true, example = "2026-10-31")
        LocalDate toDate,
        @Schema(description = "Counts for every market that has claims, by country code. Never narrowed by the market filter, "
                + "so one market can be read against the rest")
        List<MarketSummary> byMarket,
        @Schema(description = "Counts for every claim type that has claims, in the order of the claim types. Never narrowed by the "
                + "claim type filter, so one type can be read against the rest; it follows the market and the date range")
        List<ClaimTypeSummary> byClaimType,
        Instant generatedAt) {
}
