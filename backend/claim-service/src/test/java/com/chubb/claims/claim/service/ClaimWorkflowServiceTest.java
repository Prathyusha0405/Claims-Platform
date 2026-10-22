package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ApproveClaimRequest;
import com.chubb.claims.claim.dto.ClaimStatisticsResponse;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.OfficerStatusTotal;
import com.chubb.claims.claim.dto.RejectClaimRequest;
import com.chubb.claims.claim.dto.RequestInfoRequest;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.exception.NotAssignedOfficerException;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

/** The officer actions assessment-service drives: assign, approve, reject, request-info, plus statistics. */
@ExtendWith(MockitoExtension.class)
class ClaimWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);
    private static final AuthenticatedUser OTHER_OFFICER = new AuthenticatedUser(8L, "other@chubb.com", "Other Officer", Role.OFFICER);

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimHistoryRepository claimHistoryRepository;

    @Mock
    private ClaimEventPublisher eventPublisher;

    @Mock
    private ClaimNumberGenerator claimNumberGenerator;

    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(claimRepository, claimHistoryRepository, eventPublisher, claimNumberGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- assign -----------------------------------------------------------------------------------------------

    @Test
    void assignMovesASubmittedClaimUnderReviewForTheOfficerAndPublishesClaimAssigned() {
        Claim claim = submittedClaim(10L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.assignToOfficer(10L, OFFICER);

        assertThat(response.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(response.assignedOfficerId()).isEqualTo(7L);
        assertThat(claim.getUpdatedAt()).isEqualTo(NOW);
        verify(claimRepository).save(claim);

        ClaimEvent event = publishedEvent();
        assertThat(event).isEqualTo(new ClaimEvent(ClaimEventType.CLAIM_ASSIGNED, 10L, "CLM-2026-000010", 5L, 7L,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, 7L, "OFFICER", "Assigned to Demo Officer", NOW));
    }

    @Test
    void assignRefusesAClaimThatIsAlreadyBeingReviewed() {
        Claim claim = assignedClaim(10L, 8L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.assignToOfficer(10L, OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Only a SUBMITTED claim can be assigned");

        assertThat(claim.getAssignedOfficerId()).isEqualTo(8L);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void assignIsOnlyEverAllowedFromSubmitted(ClaimStatus status) {
        Claim claim = submittedClaim(10L);
        ReflectionTestUtils.setField(claim, "status", status);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.assignToOfficer(10L, OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(claim.getStatus()).isEqualTo(status);
        assertThat(claim.getAssignedOfficerId()).isNull();
    }

    @Test
    void assignOfAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.assignToOfficer(99L, OFFICER)).isInstanceOf(ClaimNotFoundException.class);
    }

    // ---- approve / reject / request-info ----------------------------------------------------------------------

    @Test
    void approveMovesTheClaimToApprovedAndRecordsTheComment() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.approve(10L, new ApproveClaimRequest("  Damage confirmed  "), OFFICER);

        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        ClaimEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(ClaimEventType.CLAIM_APPROVED);
        assertThat(event.fromStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(event.toStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(event.details()).isEqualTo("Damage confirmed");
        assertThat(event.actorId()).isEqualTo(7L);
    }

    @Test
    void approveWithoutABodyOrCommentUsesADefaultMessage() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        claimService.approve(10L, null, OFFICER);

        assertThat(publishedEvent().details()).isEqualTo("Claim approved");
    }

    @Test
    void approveTreatsABlankCommentAsNone() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        claimService.approve(10L, new ApproveClaimRequest("   "), OFFICER);

        assertThat(publishedEvent().details()).isEqualTo("Claim approved");
    }

    @Test
    void rejectMovesTheClaimToRejectedWithTheReason() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.reject(10L, new RejectClaimRequest("  Policy had lapsed  "), OFFICER);

        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(publishedEvent().type()).isEqualTo(ClaimEventType.CLAIM_REJECTED);
        assertThat(publishedEvent().details()).isEqualTo("Policy had lapsed");
    }

    @Test
    void requestInfoMovesTheClaimToMoreInfoRequiredWithTheMessage() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.requestAdditionalInfo(10L, new RequestInfoRequest(" Send photos "), OFFICER);

        assertThat(response.status()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        assertThat(publishedEvent().type()).isEqualTo(ClaimEventType.ADDITIONAL_INFO_REQUESTED);
        assertThat(publishedEvent().details()).isEqualTo("Send photos");
    }

    @Test
    void onlyTheAssignedOfficerMayDecide() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, OTHER_OFFICER)).isInstanceOf(NotAssignedOfficerException.class);
        assertThatThrownBy(() -> claimService.reject(10L, new RejectClaimRequest("no"), OTHER_OFFICER))
                .isInstanceOf(NotAssignedOfficerException.class);
        assertThatThrownBy(() -> claimService.requestAdditionalInfo(10L, new RequestInfoRequest("x"), OTHER_OFFICER))
                .isInstanceOf(NotAssignedOfficerException.class);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        verify(claimRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nobodyMayDecideAnUnassignedClaim() {
        Claim claim = submittedClaim(10L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, OFFICER)).isInstanceOf(NotAssignedOfficerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = "UNDER_REVIEW", mode = EnumSource.Mode.EXCLUDE)
    void decisionsAreOnlyAllowedWhileUnderReview(ClaimStatus status) {
        Claim claim = assignedClaim(10L, 7L);
        ReflectionTestUtils.setField(claim, "status", status);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, OFFICER)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> claimService.reject(10L, new RejectClaimRequest("no"), OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> claimService.requestAdditionalInfo(10L, new RequestInfoRequest("x"), OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(claim.getStatus()).isEqualTo(status);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void decisionOnAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.approve(99L, null, OFFICER)).isInstanceOf(ClaimNotFoundException.class);
    }

    // ---- read side --------------------------------------------------------------------------------------------

    @Test
    void getClaimByIdReturnsTheSummary() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(assignedClaim(10L, 7L)));

        ClaimSummaryResponse response = claimService.getClaimById(10L);

        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000010");
        assertThat(response.assignedOfficerId()).isEqualTo(7L);
    }

    @Test
    void getClaimByIdOfAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaimById(99L)).isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void statisticsExposeTheRepositoryAggregatesUnchanged() {
        List<StatusTotal> byStatus = List.of(new StatusTotal(ClaimStatus.SUBMITTED, 2, new BigDecimal("300.00")));
        List<OfficerStatusTotal> byOfficer = List.of(new OfficerStatusTotal(7L, ClaimStatus.UNDER_REVIEW, 1, new BigDecimal("100.00")));
        when(claimRepository.totalsByStatus()).thenReturn(byStatus);
        when(claimRepository.totalsByOfficerAndStatus()).thenReturn(byOfficer);

        ClaimStatisticsResponse statistics = claimService.statistics();

        assertThat(statistics.byStatus()).isEqualTo(byStatus);
        assertThat(statistics.byOfficer()).isEqualTo(byOfficer);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ClaimEvent publishedEvent() {
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private static Claim submittedClaim(Long id) {
        Claim claim = new Claim("CLM-2026-%06d".formatted(id), "POL-2026-1", 5L, "Jane Tan", "jane@example.com", ClaimType.AUTO,
                LocalDate.of(2026, 10, 1), "Orchard Road", "Rear-ended", new BigDecimal("2500.00"), NOW.minusSeconds(600));
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    private static Claim assignedClaim(Long id, Long officerId) {
        Claim claim = submittedClaim(id);
        claim.assignTo(officerId, NOW.minusSeconds(300));
        return claim;
    }
}
