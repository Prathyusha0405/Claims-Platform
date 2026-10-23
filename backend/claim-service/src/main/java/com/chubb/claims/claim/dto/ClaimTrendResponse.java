package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Claims submitted over time, for assessment-service's dashboard. */
public record ClaimTrendResponse(
        @Schema(description = "WEEK when the window is up to 183 days, otherwise MONTH")
        Granularity granularity,
        @Schema(description = "Ascending and gap-filled: every period between the first and the last, none skipped. Empty "
                + "when no claim matches")
        List<TrendPeriod> periods) {

    /** The size of one period. */
    public enum Granularity {
        WEEK,
        MONTH
    }
}
