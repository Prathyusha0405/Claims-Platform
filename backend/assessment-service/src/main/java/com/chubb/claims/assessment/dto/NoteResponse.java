package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.entity.ClaimNote;
import com.chubb.claims.assessment.entity.NoteType;

import java.time.Instant;

public record NoteResponse(
        Long id,
        Long claimId,
        Long officerId,
        String officerName,
        NoteType noteType,
        String content,
        Instant createdAt) {

    public static NoteResponse from(ClaimNote note) {
        return new NoteResponse(
                note.getId(),
                note.getClaimId(),
                note.getOfficerId(),
                note.getOfficerName(),
                note.getNoteType(),
                note.getContent(),
                note.getCreatedAt());
    }
}
