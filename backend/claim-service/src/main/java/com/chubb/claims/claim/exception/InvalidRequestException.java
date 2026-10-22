package com.chubb.claims.claim.exception;

/** A well-formed request whose values are unacceptable (bad date range, unsupported sort field, ...). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
