package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record OfficerWorkload(
        Long officerId,
        @Schema(description = "From the officer's token when they took their first claim through this service; may be null")
        String officerName,
        @Schema(description = "underReview + awaitingInformation: claims the officer still has to decide")
        long openClaims,
        long underReview,
        @Schema(description = "Waiting for the claimant to answer an information request")
        long awaitingInformation,
        long approved,
        long rejected,
        long settled,
        @Schema(description = "Every claim ever assigned to the officer")
        long totalAssigned,
        @Schema(description = "Sum of claimAmount over the open claims")
        BigDecimal openExposure) {
}
