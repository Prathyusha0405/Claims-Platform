package com.chubb.claims.claim.entity;

import com.chubb.claims.claim.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle:
 * <pre>
 * SUBMITTED -> UNDER_REVIEW -> MORE_INFO_REQUIRED -> UNDER_REVIEW -> APPROVED | REJECTED -> SETTLED
 * </pre>
 * Only the transitions below are valid.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    MORE_INFO_REQUIRED,
    APPROVED,
    REJECTED,
    SETTLED;

    public Set<ClaimStatus> allowedTransitions() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(MORE_INFO_REQUIRED, APPROVED, REJECTED);
            case MORE_INFO_REQUIRED -> EnumSet.of(UNDER_REVIEW);
            case APPROVED, REJECTED -> EnumSet.of(SETTLED);
            case SETTLED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedTransitions().contains(next);
    }

    public void requireTransitionTo(ClaimStatus next) {
        if (!canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(this, next);
        }
    }
}
