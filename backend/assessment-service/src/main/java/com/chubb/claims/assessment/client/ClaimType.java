package com.chubb.claims.assessment.client;

/**
 * The claim types claim-service knows. Used to check the `claimType` parameter of the dashboards before claim-service is
 * asked; claim-service owns the list.
 */
public enum ClaimType {
    AUTO,
    HOME,
    HEALTH,
    TRAVEL,
    PERSONAL_ACCIDENT,
    OTHER
}
