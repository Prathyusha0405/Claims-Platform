package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One inbox entry. {@code claimNumber} lets the UI link straight to the claim without a second lookup. */
public record NotificationResponse(
        Long id,
        Long claimId,
        @Schema(example = "CLM-2026-000042") String claimNumber,
        ClaimEventType eventType,
        String message,
        @Schema(description = "True once the recipient has opened or acknowledged it") boolean read,
        Instant createdAt) {
}
