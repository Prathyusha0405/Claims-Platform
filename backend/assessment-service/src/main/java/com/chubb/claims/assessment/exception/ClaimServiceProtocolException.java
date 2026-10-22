package com.chubb.claims.assessment.exception;

/**
 * Claim-service answered, but not in a way this service can use: it rejected our forwarded credentials (for example a
 * JWT secret mismatch), returned an unexpected status, or sent an unreadable body. Answered with 502.
 */
public class ClaimServiceProtocolException extends RuntimeException {

    public ClaimServiceProtocolException(String message) {
        super(message);
    }
}
