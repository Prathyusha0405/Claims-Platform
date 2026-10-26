package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable description of something that happened to a claim. Carries ids and values only (no entities), so a
 * Kafka publisher can serialise it as-is.
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

    /**
     * A stable id for this event: the same event has the same id wherever it is computed, so an event that a broker delivers
     * twice can be recognised. It is derived from what makes an event unique (what happened, to which claim, who did it and
     * when) rather than stored, so the record itself, and what is sent over the wire, stays as it was.
     */
    public String id() {
        String key = type + "|" + claimId + "|" + actorId + "|" + occurredAt;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
