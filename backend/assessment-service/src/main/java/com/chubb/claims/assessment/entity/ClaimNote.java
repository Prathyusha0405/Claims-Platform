package com.chubb.claims.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An immutable entry in the officers' internal review trail for a claim. */
@Entity
@Table(name = "claim_notes")
public class ClaimNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Column(name = "officer_id", nullable = false, updatable = false)
    private Long officerId;

    @Column(name = "officer_name", updatable = false, length = 100)
    private String officerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, updatable = false, length = 20)
    private NoteType noteType;

    @Column(nullable = false, updatable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClaimNote() {
        // required by JPA
    }

    public ClaimNote(Long claimId, Long officerId, String officerName, NoteType noteType, String content, Instant createdAt) {
        this.claimId = claimId;
        this.officerId = officerId;
        this.officerName = officerName;
        this.noteType = noteType;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Long getOfficerId() {
        return officerId;
    }

    public String getOfficerName() {
        return officerName;
    }

    public NoteType getNoteType() {
        return noteType;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
