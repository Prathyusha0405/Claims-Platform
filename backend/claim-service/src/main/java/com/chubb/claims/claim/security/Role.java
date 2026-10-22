package com.chubb.claims.claim.security;

/** Roles issued by auth-service in the token's {@code role} claim. */
public enum Role {
    CLAIMANT,
    OFFICER,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_OFFICER. */
    public String authority() {
        return "ROLE_" + name();
    }
}
