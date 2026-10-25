package com.chubb.claims.claim.entity;

/** Things that can happen to a claim; each one is audited in claim_history and may notify users. */
public enum ClaimEventType {
    CLAIM_CREATED,
    CLAIM_ASSIGNED,
    CLAIM_APPROVED,
    CLAIM_REJECTED,
    ADDITIONAL_INFO_REQUESTED,
    /** The claimant answered an ADDITIONAL_INFO_REQUESTED. Not in CLAUDE.md's list; needed to audit that step. */
    ADDITIONAL_INFO_RECEIVED,
    /** An approved or rejected claim was closed as settled. Not in CLAUDE.md's list; needed to audit the last lifecycle step. */
    CLAIM_SETTLED
}
