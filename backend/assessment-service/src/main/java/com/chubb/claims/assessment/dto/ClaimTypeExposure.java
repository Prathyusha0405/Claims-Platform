package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** The liability exposure of one claim type, in the reporting currency. */
public record ClaimTypeExposure(
        ClaimType claimType,
        @Schema(description = "Claims that are not SETTLED, which is what the exposure adds up")
        long claimCount,
        @Schema(description = "SUM(claimAmount) over those claims, each market's part converted and rounded to cents on its own, "
                + "so it can differ by a few cents from the same type's share of the headline exposure")
        BigDecimal exposureInReportingCurrency) {
}
