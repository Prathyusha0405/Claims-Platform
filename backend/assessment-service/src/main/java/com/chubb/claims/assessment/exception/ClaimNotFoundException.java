package com.chubb.claims.assessment.exception;

/** Claim-service has no such claim. */
public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(String message) {
        super(message);
    }
}
