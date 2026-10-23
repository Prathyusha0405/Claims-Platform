package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StaffWorkloadResponse(
        @Schema(description = "The currency each staff member's openExposure is reported in (ISO 4217)", example = "USD")
        String reportingCurrency,
        @Schema(description = "The market the figures are limited to; null when they cover every market", nullable = true)
        String market,
        @Schema(description = "The claim type the figures are limited to; null when they cover every type", nullable = true)
        ClaimType claimType,
        @Schema(description = "Only claims submitted on or after this date (UTC) are counted; null when the range has no start", nullable = true, example = "2026-08-01")
        LocalDate fromDate,
        @Schema(description = "Only claims submitted on or before this date (UTC) are counted; null when the range has no end", nullable = true, example = "2026-10-31")
        LocalDate toDate,
        @Schema(description = "Claims waiting in the work queue for any staff member to take (status SUBMITTED)")
        long unassignedClaims,
        @Schema(description = "Busiest first (most open claims), then by id")
        List<StaffWorkload> staff,
        Instant generatedAt) {
}
