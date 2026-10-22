package com.chubb.claims.assessment.entity;

public enum NoteType {
    /** A note an officer chose to add. */
    GENERAL,
    /** What the officer asked the claimant to provide. */
    INFO_REQUEST,
    /** The officer's comment when approving. */
    APPROVAL,
    /** The officer's reason for rejecting. */
    REJECTION
}
