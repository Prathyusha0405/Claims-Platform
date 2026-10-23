package com.chubb.claims.assessment.entity;

public enum NoteType {
    /** A note a staff member chose to add. */
    GENERAL,
    /** What the staff member asked the claimant to provide. */
    INFO_REQUEST,
    /** The staff member's comment when approving. */
    APPROVAL,
    /** The staff member's reason for rejecting. */
    REJECTION
}
