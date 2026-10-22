package com.chubb.claims.assessment.exception;

/** The claim is not in a state that allows the action (already assigned, not under review, ...). */
public class ClaimConflictException extends RuntimeException {

    public ClaimConflictException(String message) {
        super(message);
    }
}
