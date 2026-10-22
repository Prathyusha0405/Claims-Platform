package com.chubb.claims.auth.exception;

/** Deliberately gives no hint whether the email or the password was wrong. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
