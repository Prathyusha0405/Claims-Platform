package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.StatisticsScope;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Which claims a dashboard covers; every field is optional and supplied ones are ANDed. Bound from query-string parameters.
 * The market and the claim type are read as text so that an unknown one is answered with the list of allowed values, like
 * claim search does; dates are the claim's submission date (UTC), both ends inclusive.
 */
public record DashboardCriteria(
        @Schema(description = "Limit the headline figures to one market, by ISO country code; omit for every market", example = "SG",
                allowableValues = {"AU", "HK", "JP", "MY", "NZ", "SG"})
        String market,
        @Schema(description = "Limit the figures to one claim type; omit for every type", example = "AUTO",
                allowableValues = {"AUTO", "HOME", "HEALTH", "TRAVEL", "PERSONAL_ACCIDENT", "OTHER"})
        String claimType,
        @Schema(description = "Only claims submitted on or after this date (UTC), yyyy-MM-dd", example = "2026-08-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,
        @Schema(description = "Only claims submitted on or before this date (UTC), yyyy-MM-dd", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {

    /** Checks the values: an unknown market or type, or a range that ends before it starts, is a client error. */
    public StatisticsScope toScope() {
        return StatisticsScope.of(market, claimType, fromDate, toDate);
    }
}
