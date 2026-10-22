package com.chubb.claims.auth.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("User " + userId + " no longer exists");
    }
}
