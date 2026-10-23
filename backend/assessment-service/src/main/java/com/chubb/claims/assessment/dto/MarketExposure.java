package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** The liability exposure of one market, in its own currency and in the reporting currency. */
public record MarketExposure(
        @Schema(description = "ISO country code", example = "JP")
        String market,
        @Schema(description = "The market's currency (ISO 4217)", example = "JPY")
        String currency,
        @Schema(description = "Units of the market's currency per one unit of the reporting currency (indicative, fixed)",
                example = "150")
        BigDecimal rate,
        @Schema(description = "Claims that are not SETTLED, which is what the exposure adds up")
        long claimCount,
        @Schema(description = "Exposure in the market's own currency")
        BigDecimal exposure,
        @Schema(description = "The same exposure converted to the reporting currency")
        BigDecimal exposureInReportingCurrency) {
}
