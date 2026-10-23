package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record StaffWorkloadResponse(
        @Schema(description = "The currency each staff member's openExposure is reported in (ISO 4217)", example = "USD")
        String reportingCurrency,
        @Schema(description = "The market the figures are limited to; null when they cover every market", nullable = true)
        String market,
        @Schema(description = "Claims waiting in the work queue for any staff member to take (status SUBMITTED)")
        long unassignedClaims,
        @Schema(description = "Busiest first (most open claims), then by id")
        List<StaffWorkload> staff,
        Instant generatedAt) {
}
