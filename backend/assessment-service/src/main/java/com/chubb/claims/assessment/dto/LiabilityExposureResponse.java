package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LiabilityExposureResponse(
        @Schema(description = "The currency the amounts below are reported in (ISO 4217)", example = "USD")
        String reportingCurrency,
        @Schema(description = "The market the figures are limited to; null when they cover every market", nullable = true)
        String market,
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
        Instant generatedAt) {
}
