package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LiabilityExposureResponse(
        @Schema(description = "The currency the amounts below are reported in (ISO 4217)", example = "USD")
        String reportingCurrency,
        @Schema(description = "The market the figures are limited to; null when they cover every market", nullable = true)
        String market,
        @Schema(description = "The claim type the figures are limited to; null when they cover every type", nullable = true)
        ClaimType claimType,
        @Schema(description = "Only claims submitted on or after this date (UTC) are counted; null when the range has no start", nullable = true, example = "2026-08-01")
        LocalDate fromDate,
        @Schema(description = "Only claims submitted on or before this date (UTC) are counted; null when the range has no end", nullable = true, example = "2026-10-31")
        LocalDate toDate,
        @Schema(description = "SUM(claimAmount) over every claim whose status is not SETTLED, converted to the reporting "
                + "currency at the configured indicative rates")
        BigDecimal totalLiabilityExposure,
        @Schema(description = "How many claims make up the exposure")
        long exposedClaims,
        @Schema(description = "Total of the SETTLED claims, which the exposure excludes (reporting currency)")
        BigDecimal excludedSettledAmount,
        @Schema(example = "SUM(claimAmount) WHERE status != SETTLED")
        String formula,
        @Schema(description = "All statuses, in lifecycle order, in the reporting currency")
        List<StatusExposure> byStatus,
        @Schema(description = "Every market that has claims, in its own currency and in the reporting currency. Never "
                + "narrowed by the market filter")
        List<MarketExposure> byMarket,
        @Schema(description = "Every claim type that has claims, in the reporting currency, in the order of the claim types. Never "
                + "narrowed by the claim type filter; it follows the market and the date range")
        List<ClaimTypeExposure> byClaimType,
        Instant generatedAt) {
}
