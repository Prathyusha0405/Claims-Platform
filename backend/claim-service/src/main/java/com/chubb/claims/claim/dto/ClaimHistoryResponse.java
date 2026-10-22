package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;

import java.time.Instant;

public record ClaimHistoryResponse(
        ClaimEventType eventType,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        Long performedBy,
        String performedByRole,
        String details,
        Instant createdAt) {

    public static ClaimHistoryResponse from(ClaimHistory history) {
        return new ClaimHistoryResponse(
                history.getEventType(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getPerformedBy(),
                history.getPerformedByRole(),
                history.getDetails(),
                history.getCreatedAt());
    }
}
