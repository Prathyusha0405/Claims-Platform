package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record OfficerWorkloadResponse(
        @Schema(description = "Claims waiting in the work queue for any officer to take (status SUBMITTED)")
        long unassignedClaims,
        @Schema(description = "Busiest first (most open claims), then by officer id")
        List<OfficerWorkload> officers,
        Instant generatedAt) {
}
