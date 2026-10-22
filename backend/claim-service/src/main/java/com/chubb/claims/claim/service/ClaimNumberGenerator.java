package com.chubb.claims.claim.service;

public interface ClaimNumberGenerator {

    /** Returns the next unique, human-readable claim number, e.g. {@code CLM-2026-000042}. */
    String next();
}
