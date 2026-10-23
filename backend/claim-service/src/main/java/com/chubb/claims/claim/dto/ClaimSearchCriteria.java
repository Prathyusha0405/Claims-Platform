package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.Market;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Every field is optional; supplied filters are combined with AND. Bound from query-string parameters. */
public record ClaimSearchCriteria(

        @Schema(description = "Case-insensitive partial match", example = "CLM-2026")
        String claimNumber,

        @Schema(description = "Case-insensitive partial match", example = "POL-2026")
        String policyNumber,

        @Schema(description = "Exact status")
        ClaimStatus status,

        @Schema(description = "Exact market, by ISO country code: AU, HK, JP, MY, NZ or SG", example = "SG")
        Market market,

        @Schema(description = "Submitted on or after this date (UTC), yyyy-MM-dd", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Schema(description = "Submitted on or before this date (UTC), yyyy-MM-dd", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @Schema(description = "Id of the staff member the claim is assigned to", example = "7")
        Long assignedStaffId) {
}
