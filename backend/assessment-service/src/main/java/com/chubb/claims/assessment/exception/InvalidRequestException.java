package com.chubb.claims.assessment.exception;

/** A well-formed request whose values are unacceptable (for example an unsupported sort field). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
