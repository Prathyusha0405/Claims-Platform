package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record StatusExposure(
        ClaimStatus status,
        long claimCount,
        @Schema(description = "Sum of claimAmount for claims in this status, converted to the reporting currency")
        BigDecimal amount,
        @Schema(description = "false only for SETTLED, which the formula excludes")
        boolean includedInExposure) {
}
