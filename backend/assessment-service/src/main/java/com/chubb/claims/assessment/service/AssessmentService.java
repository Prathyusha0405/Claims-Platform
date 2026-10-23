package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.RemoteClaim;
import com.chubb.claims.assessment.dto.AddNoteRequest;
import com.chubb.claims.assessment.dto.ApproveClaimRequest;
import com.chubb.claims.assessment.dto.AssessmentResponse;
import com.chubb.claims.assessment.dto.ClaimSummaryResponse;
import com.chubb.claims.assessment.dto.NoteResponse;
import com.chubb.claims.assessment.dto.PageResponse;
import com.chubb.claims.assessment.dto.RejectClaimRequest;
import com.chubb.claims.assessment.dto.RequestInfoRequest;
import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.ClaimAssessment;
import com.chubb.claims.assessment.entity.ClaimNote;
import com.chubb.claims.assessment.entity.NoteType;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.repository.ClaimNoteRepository;
import com.chubb.claims.assessment.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The staff member's side of claim handling. Claim-service owns the claim and its lifecycle, so every state change is
 * made there first: it enforces the status rules (only SUBMITTED can be assigned, only UNDER_REVIEW can be decided)
 * and that only the assigned staff member decides, and it records the audit trail and notifies the claimant. This service
 * then records what is the staff member's own: the assessment (who took the claim, and the decision) and the review notes.
 * <p>
 * Order matters: the claim-service call comes first and the local write last. If claim-service refuses, nothing is
 * stored here. If the local write fails after claim-service accepted, the claim has still moved correctly, and the
 * missing assessment record is recreated by the staff member's next action on that claim.
 */
@Service
public class AssessmentService {

    private final ClaimServiceClient claimServiceClient;
    private final ClaimAssessmentRepository assessmentRepository;
    private final ClaimNoteRepository noteRepository;
    private final Clock clock;

    public AssessmentService(ClaimServiceClient claimServiceClient,
                             ClaimAssessmentRepository assessmentRepository,
                             ClaimNoteRepository noteRepository,
                             Clock clock) {
        this.claimServiceClient = claimServiceClient;
        this.assessmentRepository = assessmentRepository;
        this.noteRepository = noteRepository;
        this.clock = clock;
    }

    /** The work queue: SUBMITTED claims nobody has taken yet, optionally for one market. */
    public PageResponse<ClaimSummaryResponse> getUnassignedClaims(String market, Pageable pageable) {
        return PageResponse.from(claimServiceClient.findUnassigned(market, pageable), ClaimSummaryResponse::from);
    }

    /** The staff member takes an unassigned claim; it moves to UNDER_REVIEW. */
    @Transactional
    public AssessmentResponse assign(Long claimId, AuthenticatedUser staffMember) {
        RemoteClaim claim = claimServiceClient.assign(claimId);
        Instant now = clock.instant();

        ClaimAssessment assessment = assessmentRepository.findByClaimId(claim.id())
                .map(existing -> {
                    existing.assignTo(staffMember.userId(), staffMember.fullName(), now);
                    return existing;
                })
                .orElseGet(() -> new ClaimAssessment(claim.id(), claim.claimNumber(), staffMember.userId(), staffMember.fullName(), now));

        return AssessmentResponse.from(assessmentRepository.save(assessment), claim.status());
    }

    /** UNDER_REVIEW -> APPROVED. The comment is optional. */
    @Transactional
    public AssessmentResponse approve(Long claimId, ApproveClaimRequest request, AuthenticatedUser staffMember) {
        String comment = request == null ? null : trimToNull(request.comment());
        RemoteClaim claim = claimServiceClient.approve(claimId, comment);

        Instant now = clock.instant();
        ClaimAssessment assessment = assessmentFor(claim, staffMember, now);
        assessment.recordDecision(AssessmentDecision.APPROVED, comment, now);
        if (comment != null) {
            noteRepository.save(new ClaimNote(claim.id(), staffMember.userId(), staffMember.fullName(), NoteType.APPROVAL, comment, now));
        }
        return AssessmentResponse.from(assessmentRepository.save(assessment), claim.status());
    }

    /** UNDER_REVIEW -> REJECTED. A reason is required. */
    @Transactional
    public AssessmentResponse reject(Long claimId, RejectClaimRequest request, AuthenticatedUser staffMember) {
        String reason = request.reason().trim();
        RemoteClaim claim = claimServiceClient.reject(claimId, reason);

        Instant now = clock.instant();
        ClaimAssessment assessment = assessmentFor(claim, staffMember, now);
        assessment.recordDecision(AssessmentDecision.REJECTED, reason, now);
        noteRepository.save(new ClaimNote(claim.id(), staffMember.userId(), staffMember.fullName(), NoteType.REJECTION, reason, now));
        return AssessmentResponse.from(assessmentRepository.save(assessment), claim.status());
    }

    /** UNDER_REVIEW -> MORE_INFO_REQUIRED. Tells the claimant what to provide; no decision is recorded yet. */
    @Transactional
    public AssessmentResponse requestInfo(Long claimId, RequestInfoRequest request, AuthenticatedUser staffMember) {
        String message = request.message().trim();
        RemoteClaim claim = claimServiceClient.requestInfo(claimId, message);

        Instant now = clock.instant();
        ClaimAssessment assessment = assessmentFor(claim, staffMember, now);
        noteRepository.save(new ClaimNote(claim.id(), staffMember.userId(), staffMember.fullName(), NoteType.INFO_REQUEST, message, now));
        return AssessmentResponse.from(assessmentRepository.save(assessment), claim.status());
    }

    /** Adds an internal note to any existing claim. */
    @Transactional
    public NoteResponse addNote(Long claimId, AddNoteRequest request, AuthenticatedUser staffMember) {
        RemoteClaim claim = claimServiceClient.getClaim(claimId);

        ClaimNote note = noteRepository.save(new ClaimNote(claim.id(), staffMember.userId(), staffMember.fullName(),
                NoteType.GENERAL, request.content().trim(), clock.instant()));
        return NoteResponse.from(note);
    }

    /**
     * The review trail for a claim, oldest first: general notes plus the notes recorded by approvals, rejections and
     * information requests. Any staff member may read any claim's notes, as any staff member may add one. The claim is looked up
     * first so an unknown claim is a 404 (and a claim-service outage a 503) rather than an empty list; that call is
     * deliberately outside any transaction.
     */
    public List<NoteResponse> getNotes(Long claimId) {
        claimServiceClient.getClaim(claimId);
        return noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claimId).stream().map(NoteResponse::from).toList();
    }

    private ClaimAssessment assessmentFor(RemoteClaim claim, AuthenticatedUser staffMember, Instant now) {
        return assessmentRepository.findByClaimId(claim.id())
                .orElseGet(() -> new ClaimAssessment(claim.id(), claim.claimNumber(), staffMember.userId(), staffMember.fullName(), now));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
