package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Narrows the statistics to some of the claims; every field is optional and supplied ones are ANDed. Bound from query-string
 * parameters. Dates are the claim's submission date (UTC) and both ends are inclusive, as on claim search.
 */
public record StatisticsCriteria(
        @Schema(description = "Only claims of this type", example = "AUTO")
        ClaimType claimType,
        @Schema(description = "Submitted on or after this date (UTC), yyyy-MM-dd", example = "2026-08-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,
        @Schema(description = "Submitted on or before this date (UTC), yyyy-MM-dd", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {
}
