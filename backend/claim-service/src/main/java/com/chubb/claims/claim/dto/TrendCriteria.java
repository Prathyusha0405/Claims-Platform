package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** The claims a trend is drawn from; every field is optional and supplied ones are ANDed. Bound from query-string parameters. */
public record TrendCriteria(
        @Schema(description = "Only claims of this market, by ISO country code: AU, HK, JP, MY, NZ or SG", example = "SG")
        Market market,
        @Schema(description = "Only claims of this type", example = "AUTO")
        ClaimType claimType,
        @Schema(description = "Submitted on or after this date (UTC), yyyy-MM-dd", example = "2026-08-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,
        @Schema(description = "Submitted on or before this date (UTC), yyyy-MM-dd", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {
}
