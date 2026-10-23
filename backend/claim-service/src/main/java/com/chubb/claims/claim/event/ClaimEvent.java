package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;

import java.time.Instant;

/**
 * Immutable description of something that happened to a claim. Carries ids and values only (no entities), so a
 * future broker-backed publisher can serialise it as-is.
 *
 * @param assignedStaffId staff member the claim is assigned to at the time of the event, if any
 * @param fromStatus        status before the event; null when the event did not change status (e.g. creation)
 * @param actorId           user who caused the event
 * @param details           free text recorded in the audit trail (notes, requested/provided information)
 */
public record ClaimEvent(
        ClaimEventType type,
        Long claimId,
        String claimNumber,
        Long claimantId,
        Long assignedStaffId,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        Long actorId,
        String actorRole,
        String details,
        Instant occurredAt) {
}
