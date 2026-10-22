package com.chubb.claims.claim.exception;

/** Also used when the notification exists but is someone else's, so ids cannot be probed. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("Notification '" + id + "' was not found");
    }
}
