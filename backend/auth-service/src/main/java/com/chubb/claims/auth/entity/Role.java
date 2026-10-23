package com.chubb.claims.auth.entity;

public enum Role {
    CLAIMANT,
    CLAIMS_STAFF,
    MANAGER;

    /** Spring Security authority name, e.g. ROLE_CLAIMS_STAFF. */
    public String authority() {
        return "ROLE_" + name();
    }
}
