package com.chubb.claims.claim.exception;

/** Also used when a claim exists but belongs to someone else, so claim numbers cannot be probed. */
public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(String identifier) {
        super("Claim '" + identifier + "' was not found");
    }
}
