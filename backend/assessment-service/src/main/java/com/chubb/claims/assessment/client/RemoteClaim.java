package com.chubb.claims.assessment.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A claim as claim-service returns it (its ClaimSummaryResponse). Unknown fields are ignored. */
public record RemoteClaim(
        Long id,
        String claimNumber,
        String policyNumber,
        String claimType,
        ClaimStatus status,
        BigDecimal claimAmount,
        LocalDate incidentDate,
        Long assignedOfficerId,
        Instant createdAt,
        Instant updatedAt) {
}
