package com.chubb.claims.claim.exception;

/** An officer tried to decide a claim that is not assigned to them. */
public class NotAssignedOfficerException extends RuntimeException {

    public NotAssignedOfficerException() {
        super("This claim is not assigned to you");
    }
}
