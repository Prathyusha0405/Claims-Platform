package com.chubb.claims.auth.entity;

public enum Role {
    CLAIMANT,
    OFFICER,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_OFFICER. */
    public String authority() {
        return "ROLE_" + name();
    }
}
