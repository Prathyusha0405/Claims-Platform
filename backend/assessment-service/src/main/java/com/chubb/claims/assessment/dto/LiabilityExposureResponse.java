package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LiabilityExposureResponse(
        @Schema(description = "SUM(claimAmount) over every claim whose status is not SETTLED")
        BigDecimal totalLiabilityExposure,
        @Schema(description = "How many claims make up the exposure")
        long exposedClaims,
        @Schema(description = "Total of the SETTLED claims, which the exposure excludes")
        BigDecimal excludedSettledAmount,
        @Schema(example = "SUM(claimAmount) WHERE status != SETTLED")
        String formula,
        @Schema(description = "All statuses, in lifecycle order")
        List<StatusExposure> byStatus,
        Instant generatedAt) {
}
