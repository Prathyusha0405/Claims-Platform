package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** How many claims were submitted in each week or month, and where they stand now. */
public record ClaimsOverTimeResponse(
        @Schema(description = "WEEK when the window is up to 183 days, otherwise MONTH")
        Granularity granularity,
        @Schema(description = "The market the figures are limited to; null when they cover every market", nullable = true)
        String market,
        @Schema(description = "The claim type the figures are limited to; null when they cover every type", nullable = true)
        ClaimType claimType,
        @Schema(description = "Only claims submitted on or after this date (UTC) are counted; null when the range has no start", nullable = true, example = "2026-08-01")
        LocalDate fromDate,
        @Schema(description = "Only claims submitted on or before this date (UTC) are counted; null when the range has no end", nullable = true, example = "2026-10-31")
        LocalDate toDate,
        @Schema(description = "Ascending and gap-filled: every period between the first and the last, none skipped. Empty when no "
                + "claim matches")
        List<ClaimsPeriod> periods,
        Instant generatedAt) {

    /** The size of one period. */
    public enum Granularity {
        WEEK,
        MONTH
    }
}
