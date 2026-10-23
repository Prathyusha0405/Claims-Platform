package com.chubb.claims.claim.security;

/** Roles issued by auth-service in the token's {@code role} claim. */
public enum Role {
    CLAIMANT,
    CLAIMS_STAFF,
    MANAGER;

    /** Spring Security authority name, e.g. ROLE_CLAIMS_STAFF. */
    public String authority() {
        return "ROLE_" + name();
    }
}
