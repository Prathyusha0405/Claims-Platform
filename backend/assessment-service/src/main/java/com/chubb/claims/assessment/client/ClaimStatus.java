package com.chubb.claims.assessment.client;

/** The claim lifecycle as claim-service reports it; claim-service owns the transitions. */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    MORE_INFO_REQUIRED,
    APPROVED,
    REJECTED,
    SETTLED;

    /** Not yet decided: still waiting for a staff member, being reviewed, or waiting for the claimant. */
    public boolean isPending() {
        return this == SUBMITTED || this == UNDER_REVIEW || this == MORE_INFO_REQUIRED;
    }

    /** Assigned to a staff member and not yet decided. */
    public boolean isOpenForStaff() {
        return this == UNDER_REVIEW || this == MORE_INFO_REQUIRED;
    }

    /** Liability exposure counts every claim that is not yet SETTLED. */
    public boolean countsTowardLiability() {
        return this != SETTLED;
    }
}
