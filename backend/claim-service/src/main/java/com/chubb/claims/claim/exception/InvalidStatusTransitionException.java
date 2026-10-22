package com.chubb.claims.claim.exception;

import com.chubb.claims.claim.entity.ClaimStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to + "; allowed next statuses: " + from.allowedTransitions());
    }

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
