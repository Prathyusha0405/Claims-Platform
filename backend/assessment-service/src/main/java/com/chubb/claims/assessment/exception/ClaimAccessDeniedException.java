package com.chubb.claims.assessment.exception;

/** The officer is not allowed to act on this claim, typically because it is assigned to someone else. */
public class ClaimAccessDeniedException extends RuntimeException {

    public ClaimAccessDeniedException(String message) {
        super(message);
    }
}
