package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteClaim;
import com.chubb.claims.assessment.client.RemotePage;
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
import com.chubb.claims.assessment.exception.ClaimAccessDeniedException;
import com.chubb.claims.assessment.exception.ClaimConflictException;
import com.chubb.claims.assessment.exception.ClaimNotFoundException;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.repository.ClaimNoteRepository;
import com.chubb.claims.assessment.security.AuthenticatedUser;
import com.chubb.claims.assessment.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);

    @Mock
    private ClaimServiceClient claimServiceClient;

    @Mock
    private ClaimAssessmentRepository assessmentRepository;

    @Mock
    private ClaimNoteRepository noteRepository;

    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentService = new AssessmentService(claimServiceClient, assessmentRepository, noteRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- work queue -------------------------------------------------------------------------------------------

    @Test
    void theWorkQueueMapsClaimServicesPage() {
        Pageable pageable = PageRequest.of(1, 5);
        when(claimServiceClient.findUnassigned(pageable)).thenReturn(new RemotePage<>(
                List.of(claim(10L, ClaimStatus.SUBMITTED, null)), 1, 5, 6, 2, false, true));

        PageResponse<ClaimSummaryResponse> page = assessmentService.getUnassignedClaims(pageable);

        assertThat(page.content()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(10L);
            assertThat(summary.claimNumber()).isEqualTo("CLM-2026-000010");
            assertThat(summary.status()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(summary.claimAmount()).isEqualByComparingTo("2500.00");
            assertThat(summary.assignedOfficerId()).isNull();
        });
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
    }

    // ---- assign -----------------------------------------------------------------------------------------------

    @Test
    void assignRecordsTheOfficersAssessmentAfterClaimServiceAccepts() {
        when(claimServiceClient.assign(10L)).thenReturn(claim(10L, ClaimStatus.UNDER_REVIEW, 7L));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.empty());
        savedAssessmentsAreReturned();

        AssessmentResponse response = assessmentService.assign(10L, OFFICER);

        ArgumentCaptor<ClaimAssessment> saved = ArgumentCaptor.forClass(ClaimAssessment.class);
        verify(assessmentRepository).save(saved.capture());
        assertThat(saved.getValue().getClaimId()).isEqualTo(10L);
        assertThat(saved.getValue().getClaimNumber()).isEqualTo("CLM-2026-000010");
        assertThat(saved.getValue().getOfficerId()).isEqualTo(7L);
        assertThat(saved.getValue().getOfficerName()).isEqualTo("Demo Officer");
        assertThat(saved.getValue().getAssignedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getDecision()).isNull();

        assertThat(response.claimStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(response.officerId()).isEqualTo(7L);
        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000010");
    }

    @Test
    void assignReusesAnExistingAssessmentRowAndClearsAnyStaleDecision() {
        ClaimAssessment stale = new ClaimAssessment(10L, "CLM-2026-000010", 3L, "Old Officer", NOW.minusSeconds(3600));
        stale.recordDecision(AssessmentDecision.REJECTED, "old", NOW.minusSeconds(60));
        when(claimServiceClient.assign(10L)).thenReturn(claim(10L, ClaimStatus.UNDER_REVIEW, 7L));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.of(stale));
        savedAssessmentsAreReturned();

        assessmentService.assign(10L, OFFICER);

        assertThat(stale.getOfficerId()).isEqualTo(7L);
        assertThat(stale.getAssignedAt()).isEqualTo(NOW);
        assertThat(stale.getDecision()).isNull();
        assertThat(stale.getDecidedAt()).isNull();
    }

    @Test
    void assignStoresNothingWhenClaimServiceRefuses() {
        when(claimServiceClient.assign(10L)).thenThrow(new ClaimConflictException("Only a SUBMITTED claim can be assigned"));

        assertThatThrownBy(() -> assessmentService.assign(10L, OFFICER)).isInstanceOf(ClaimConflictException.class);

        verifyNoInteractions(assessmentRepository, noteRepository);
    }

    // ---- approve ----------------------------------------------------------------------------------------------

    @Test
    void approveRecordsTheDecisionAndACommentNote() {
        when(claimServiceClient.approve(10L, "Damage confirmed")).thenReturn(claim(10L, ClaimStatus.APPROVED, 7L));
        ClaimAssessment existing = new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Demo Officer", NOW.minusSeconds(600));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.of(existing));
        savedAssessmentsAreReturned();

        AssessmentResponse response = assessmentService.approve(10L, new ApproveClaimRequest("  Damage confirmed  "), OFFICER);

        assertThat(existing.getDecision()).isEqualTo(AssessmentDecision.APPROVED);
        assertThat(existing.getDecisionReason()).isEqualTo("Damage confirmed");
        assertThat(existing.getDecidedAt()).isEqualTo(NOW);
        assertThat(existing.getAssignedAt()).isEqualTo(NOW.minusSeconds(600));

        ClaimNote note = savedNote();
        assertThat(note.getNoteType()).isEqualTo(NoteType.APPROVAL);
        assertThat(note.getContent()).isEqualTo("Damage confirmed");
        assertThat(note.getClaimId()).isEqualTo(10L);
        assertThat(note.getOfficerId()).isEqualTo(7L);

        assertThat(response.claimStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.decision()).isEqualTo(AssessmentDecision.APPROVED);
    }

    @Test
    void approveWithoutABodyOrCommentRecordsTheDecisionButNoNote() {
        when(claimServiceClient.approve(10L, null)).thenReturn(claim(10L, ClaimStatus.APPROVED, 7L));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.empty());
        savedAssessmentsAreReturned();

        AssessmentResponse noBody = assessmentService.approve(10L, null, OFFICER);
        AssessmentResponse blank = assessmentService.approve(10L, new ApproveClaimRequest("   "), OFFICER);

        assertThat(noBody.decision()).isEqualTo(AssessmentDecision.APPROVED);
        assertThat(noBody.decisionReason()).isNull();
        assertThat(blank.decision()).isEqualTo(AssessmentDecision.APPROVED);
        verifyNoInteractions(noteRepository);
    }

    @Test
    void approveCreatesTheAssessmentWhenAnEarlierLocalWriteWasLost() {
        when(claimServiceClient.approve(10L, null)).thenReturn(claim(10L, ClaimStatus.APPROVED, 7L));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.empty());
        savedAssessmentsAreReturned();

        assessmentService.approve(10L, null, OFFICER);

        ArgumentCaptor<ClaimAssessment> saved = ArgumentCaptor.forClass(ClaimAssessment.class);
        verify(assessmentRepository).save(saved.capture());
        assertThat(saved.getValue().getOfficerId()).isEqualTo(7L);
        assertThat(saved.getValue().getDecision()).isEqualTo(AssessmentDecision.APPROVED);
    }

    @Test
    void anotherOfficersClaimCannotBeApprovedAndNothingIsStored() {
        when(claimServiceClient.approve(10L, null)).thenThrow(new ClaimAccessDeniedException("This claim is not assigned to you"));

        assertThatThrownBy(() -> assessmentService.approve(10L, null, OFFICER))
                .isInstanceOf(ClaimAccessDeniedException.class)
                .hasMessage("This claim is not assigned to you");

        verifyNoInteractions(assessmentRepository, noteRepository);
    }

    // ---- reject -----------------------------------------------------------------------------------------------

    @Test
    void rejectRecordsTheReasonAsDecisionAndNote() {
        when(claimServiceClient.reject(10L, "Policy lapsed")).thenReturn(claim(10L, ClaimStatus.REJECTED, 7L));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.empty());
        savedAssessmentsAreReturned();

        AssessmentResponse response = assessmentService.reject(10L, new RejectClaimRequest("  Policy lapsed "), OFFICER);

        assertThat(response.decision()).isEqualTo(AssessmentDecision.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Policy lapsed");
        assertThat(response.decidedAt()).isEqualTo(NOW);
        assertThat(response.claimStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(savedNote().getNoteType()).isEqualTo(NoteType.REJECTION);
        assertThat(savedNote().getContent()).isEqualTo("Policy lapsed");
    }

    @Test
    void rejectStoresNothingWhenTheClaimIsNotUnderReview() {
        when(claimServiceClient.reject(10L, "no")).thenThrow(new ClaimConflictException("Cannot move a claim from APPROVED to REJECTED"));

        assertThatThrownBy(() -> assessmentService.reject(10L, new RejectClaimRequest("no"), OFFICER))
                .isInstanceOf(ClaimConflictException.class);

        verifyNoInteractions(assessmentRepository, noteRepository);
    }

    // ---- request-info -----------------------------------------------------------------------------------------

    @Test
    void requestInfoNotesTheRequestButRecordsNoDecision() {
        when(claimServiceClient.requestInfo(10L, "Send photos")).thenReturn(claim(10L, ClaimStatus.MORE_INFO_REQUIRED, 7L));
        ClaimAssessment existing = new ClaimAssessment(10L, "CLM-2026-000010", 7L, "Demo Officer", NOW.minusSeconds(600));
        when(assessmentRepository.findByClaimId(10L)).thenReturn(Optional.of(existing));
        savedAssessmentsAreReturned();

        AssessmentResponse response = assessmentService.requestInfo(10L, new RequestInfoRequest(" Send photos "), OFFICER);

        assertThat(response.claimStatus()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        assertThat(response.decision()).isNull();
        assertThat(existing.getDecision()).isNull();
        assertThat(savedNote().getNoteType()).isEqualTo(NoteType.INFO_REQUEST);
        assertThat(savedNote().getContent()).isEqualTo("Send photos");
    }

    // ---- notes ------------------------------------------------------------------------------------------------

    @Test
    void anOfficerCanNoteAnyExistingClaim() {
        when(claimServiceClient.getClaim(10L)).thenReturn(claim(10L, ClaimStatus.SUBMITTED, null));
        when(noteRepository.save(any(ClaimNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse response = assessmentService.addNote(10L, new AddNoteRequest("  Workshop estimate looks inflated  "), OFFICER);

        assertThat(response.claimId()).isEqualTo(10L);
        assertThat(response.officerId()).isEqualTo(7L);
        assertThat(response.officerName()).isEqualTo("Demo Officer");
        assertThat(response.noteType()).isEqualTo(NoteType.GENERAL);
        assertThat(response.content()).isEqualTo("Workshop estimate looks inflated");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aNoteOnAnUnknownClaimIsRejectedAndNotStored() {
        when(claimServiceClient.getClaim(99L)).thenThrow(new ClaimNotFoundException("Claim '99' was not found"));

        assertThatThrownBy(() -> assessmentService.addNote(99L, new AddNoteRequest("hi"), OFFICER))
                .isInstanceOf(ClaimNotFoundException.class);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void claimServiceOutagesPropagateUntouched() {
        when(claimServiceClient.getClaim(10L)).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        assertThatThrownBy(() -> assessmentService.addNote(10L, new AddNoteRequest("hi"), OFFICER))
                .isInstanceOf(ClaimServiceUnavailableException.class);
    }

    // ---- reading notes ----------------------------------------------------------------------------------------

    @Test
    void theReviewTrailIsReadOldestFirstAfterConfirmingTheClaimExists() {
        when(claimServiceClient.getClaim(10L)).thenReturn(claim(10L, ClaimStatus.UNDER_REVIEW, 7L));
        ClaimNote infoRequest = new ClaimNote(10L, 7L, "Demo Officer", NoteType.INFO_REQUEST, "Send photos", NOW.minusSeconds(300));
        ClaimNote general = new ClaimNote(10L, 8L, "Other Officer", NoteType.GENERAL, "Called the workshop", NOW);
        when(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(10L)).thenReturn(List.of(infoRequest, general));

        List<NoteResponse> trail = assessmentService.getNotes(10L);

        assertThat(trail).extracting(NoteResponse::content).containsExactly("Send photos", "Called the workshop");
        assertThat(trail).extracting(NoteResponse::noteType).containsExactly(NoteType.INFO_REQUEST, NoteType.GENERAL);
        assertThat(trail).extracting(NoteResponse::officerName).containsExactly("Demo Officer", "Other Officer");
        assertThat(trail).extracting(NoteResponse::createdAt).containsExactly(NOW.minusSeconds(300), NOW);
        assertThat(trail).extracting(NoteResponse::claimId).containsOnly(10L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(claimServiceClient, noteRepository);
        order.verify(claimServiceClient).getClaim(10L);
        order.verify(noteRepository).findByClaimIdOrderByCreatedAtAscIdAsc(10L);
    }

    @Test
    void aClaimWithoutNotesHasAnEmptyTrail() {
        when(claimServiceClient.getClaim(10L)).thenReturn(claim(10L, ClaimStatus.SUBMITTED, null));
        when(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(10L)).thenReturn(List.of());

        assertThat(assessmentService.getNotes(10L)).isEmpty();
    }

    @Test
    void theNotesOfAnUnknownClaimAreA404NotAnEmptyList() {
        when(claimServiceClient.getClaim(99L)).thenThrow(new ClaimNotFoundException("Claim '99' was not found"));

        assertThatThrownBy(() -> assessmentService.getNotes(99L)).isInstanceOf(ClaimNotFoundException.class);

        verifyNoInteractions(noteRepository);
    }

    @Test
    void readingNotesDuringAClaimServiceOutagePropagatesTheOutage() {
        when(claimServiceClient.getClaim(10L)).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        assertThatThrownBy(() -> assessmentService.getNotes(10L)).isInstanceOf(ClaimServiceUnavailableException.class);

        verifyNoInteractions(noteRepository);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private void savedAssessmentsAreReturned() {
        when(assessmentRepository.save(any(ClaimAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ClaimNote savedNote() {
        ArgumentCaptor<ClaimNote> captor = ArgumentCaptor.forClass(ClaimNote.class);
        verify(noteRepository).save(captor.capture());
        return captor.getValue();
    }

    private static RemoteClaim claim(Long id, ClaimStatus status, Long officerId) {
        return new RemoteClaim(id, "CLM-2026-%06d".formatted(id), "POL-2026-1", "AUTO", status, new BigDecimal("2500.00"),
                LocalDate.of(2026, 10, 1), officerId, NOW.minusSeconds(600), NOW);
    }
}
