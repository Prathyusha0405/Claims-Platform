package com.chubb.claims.claim.exception;

/** A staff member tried to decide a claim that is not assigned to them. */
public class NotAssignedStaffException extends RuntimeException {

    public NotAssignedStaffException() {
        super("This claim is not assigned to you");
    }
}
