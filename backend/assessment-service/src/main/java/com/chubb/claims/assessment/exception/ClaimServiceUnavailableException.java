package com.chubb.claims.assessment.exception;

/** Claim-service could not be reached, timed out, or failed with a 5xx. Answered with 503. */
public class ClaimServiceUnavailableException extends RuntimeException {

    public ClaimServiceUnavailableException(String message) {
        super(message);
    }
}
