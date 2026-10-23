package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ApproveClaimRequest;
import com.chubb.claims.claim.dto.ClaimStatisticsResponse;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.StaffStatusTotal;
import com.chubb.claims.claim.dto.RejectClaimRequest;
import com.chubb.claims.claim.dto.RequestInfoRequest;
import com.chubb.claims.claim.dto.StatisticsCriteria;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.dto.TypeStatusTotal;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.exception.NotAssignedStaffException;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.ClaimScope;
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

/** The staff member actions assessment-service drives: assign, approve, reject, request-info, plus statistics. */
@ExtendWith(MockitoExtension.class)
class ClaimWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final AuthenticatedUser CLAIMS_STAFF = new AuthenticatedUser(7L, "staff@chubb.com", "Demo Claims Staff", Role.CLAIMS_STAFF);
    private static final AuthenticatedUser OTHER_STAFF = new AuthenticatedUser(8L, "other@chubb.com", "Other Staff", Role.CLAIMS_STAFF);

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
    void assignMovesASubmittedClaimUnderReviewForTheStaffMemberAndPublishesClaimAssigned() {
        Claim claim = submittedClaim(10L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.assignToStaff(10L, CLAIMS_STAFF);

        assertThat(response.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(response.assignedStaffId()).isEqualTo(7L);
        assertThat(claim.getUpdatedAt()).isEqualTo(NOW);
        verify(claimRepository).save(claim);

        ClaimEvent event = publishedEvent();
        assertThat(event).isEqualTo(new ClaimEvent(ClaimEventType.CLAIM_ASSIGNED, 10L, "CLM-2026-000010", 5L, 7L,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, 7L, "CLAIMS_STAFF", "Assigned to Demo Claims Staff", NOW));
    }

    @Test
    void assignRefusesAClaimThatIsAlreadyBeingReviewed() {
        Claim claim = assignedClaim(10L, 8L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.assignToStaff(10L, CLAIMS_STAFF))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Only a SUBMITTED claim can be assigned");

        assertThat(claim.getAssignedStaffId()).isEqualTo(8L);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void assignIsOnlyEverAllowedFromSubmitted(ClaimStatus status) {
        Claim claim = submittedClaim(10L);
        ReflectionTestUtils.setField(claim, "status", status);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.assignToStaff(10L, CLAIMS_STAFF))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(claim.getStatus()).isEqualTo(status);
        assertThat(claim.getAssignedStaffId()).isNull();
    }

    @Test
    void assignOfAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.assignToStaff(99L, CLAIMS_STAFF)).isInstanceOf(ClaimNotFoundException.class);
    }

    // ---- approve / reject / request-info ----------------------------------------------------------------------

    @Test
    void approveMovesTheClaimToApprovedAndRecordsTheComment() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.approve(10L, new ApproveClaimRequest("  Damage confirmed  "), CLAIMS_STAFF);

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

        claimService.approve(10L, null, CLAIMS_STAFF);

        assertThat(publishedEvent().details()).isEqualTo("Claim approved");
    }

    @Test
    void approveTreatsABlankCommentAsNone() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        claimService.approve(10L, new ApproveClaimRequest("   "), CLAIMS_STAFF);

        assertThat(publishedEvent().details()).isEqualTo("Claim approved");
    }

    @Test
    void rejectMovesTheClaimToRejectedWithTheReason() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.reject(10L, new RejectClaimRequest("  Policy had lapsed  "), CLAIMS_STAFF);

        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(publishedEvent().type()).isEqualTo(ClaimEventType.CLAIM_REJECTED);
        assertThat(publishedEvent().details()).isEqualTo("Policy had lapsed");
    }

    @Test
    void requestInfoMovesTheClaimToMoreInfoRequiredWithTheMessage() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimSummaryResponse response = claimService.requestAdditionalInfo(10L, new RequestInfoRequest(" Send photos "), CLAIMS_STAFF);

        assertThat(response.status()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        assertThat(publishedEvent().type()).isEqualTo(ClaimEventType.ADDITIONAL_INFO_REQUESTED);
        assertThat(publishedEvent().details()).isEqualTo("Send photos");
    }

    @Test
    void onlyTheAssignedStaffMemberMayDecide() {
        Claim claim = assignedClaim(10L, 7L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, OTHER_STAFF)).isInstanceOf(NotAssignedStaffException.class);
        assertThatThrownBy(() -> claimService.reject(10L, new RejectClaimRequest("no"), OTHER_STAFF))
                .isInstanceOf(NotAssignedStaffException.class);
        assertThatThrownBy(() -> claimService.requestAdditionalInfo(10L, new RequestInfoRequest("x"), OTHER_STAFF))
                .isInstanceOf(NotAssignedStaffException.class);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        verify(claimRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nobodyMayDecideAnUnassignedClaim() {
        Claim claim = submittedClaim(10L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, CLAIMS_STAFF)).isInstanceOf(NotAssignedStaffException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = "UNDER_REVIEW", mode = EnumSource.Mode.EXCLUDE)
    void decisionsAreOnlyAllowedWhileUnderReview(ClaimStatus status) {
        Claim claim = assignedClaim(10L, 7L);
        ReflectionTestUtils.setField(claim, "status", status);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(10L, null, CLAIMS_STAFF)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> claimService.reject(10L, new RejectClaimRequest("no"), CLAIMS_STAFF))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> claimService.requestAdditionalInfo(10L, new RequestInfoRequest("x"), CLAIMS_STAFF))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(claim.getStatus()).isEqualTo(status);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void decisionOnAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.approve(99L, null, CLAIMS_STAFF)).isInstanceOf(ClaimNotFoundException.class);
    }

    // ---- read side --------------------------------------------------------------------------------------------

    @Test
    void getClaimByIdReturnsTheSummary() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(assignedClaim(10L, 7L)));

        ClaimSummaryResponse response = claimService.getClaimById(10L);

        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000010");
        assertThat(response.assignedStaffId()).isEqualTo(7L);
    }

    @Test
    void getClaimByIdOfAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaimById(99L)).isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void statisticsExposeTheRepositoryAggregatesUnchanged() {
        List<StatusTotal> byStatus = List.of(new StatusTotal(Market.SG, Currency.SGD, ClaimStatus.SUBMITTED, 2, new BigDecimal("300.00")));
        List<StaffStatusTotal> byStaff = List.of(new StaffStatusTotal(7L, Market.SG, Currency.SGD, ClaimStatus.UNDER_REVIEW, 1, new BigDecimal("100.00")));
        List<TypeStatusTotal> byType = List.of(new TypeStatusTotal(Market.SG, Currency.SGD, ClaimType.AUTO, ClaimStatus.SUBMITTED, 2, new BigDecimal("300.00")));
        when(claimRepository.totalsByStatus(ClaimScope.all())).thenReturn(byStatus);
        when(claimRepository.totalsByStaffAndStatus(ClaimScope.all())).thenReturn(byStaff);
        when(claimRepository.totalsByTypeAndStatus(ClaimScope.all())).thenReturn(byType);

        ClaimStatisticsResponse statistics = claimService.statistics(new StatisticsCriteria(null, null, null));

        assertThat(statistics.byStatus()).isEqualTo(byStatus);
        assertThat(statistics.byStaff()).isEqualTo(byStaff);
        assertThat(statistics.byType()).isEqualTo(byType);
    }

    @Test
    void statisticsAskTheRepositoryAboutTheClaimsTheCriteriaSelect() {
        StatisticsCriteria criteria = new StatisticsCriteria(ClaimType.HOME, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31));
        ClaimScope scope = ClaimScope.of(criteria);

        claimService.statistics(criteria);

        assertThat(scope.claimType()).isEqualTo(ClaimType.HOME);
        assertThat(scope.from()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(scope.toExclusive()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        verify(claimRepository).totalsByStatus(scope);
        verify(claimRepository).totalsByStaffAndStatus(scope);
        verify(claimRepository).totalsByTypeAndStatus(scope);
    }

    @Test
    void statisticsRefuseAWindowThatEndsBeforeItStarts() {
        StatisticsCriteria backwards = new StatisticsCriteria(null, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> claimService.statistics(backwards))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("fromDate must not be after toDate");
        verifyNoInteractions(claimRepository);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ClaimEvent publishedEvent() {
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private static Claim submittedClaim(Long id) {
        Claim claim = new Claim("CLM-2026-%06d".formatted(id), "POL-2026-1", 5L, "Jane Tan", "jane@example.com", ClaimType.AUTO, Market.SG,
                LocalDate.of(2026, 10, 1), "Orchard Road", "Rear-ended", new BigDecimal("2500.00"), NOW.minusSeconds(600));
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    private static Claim assignedClaim(Long id, Long staffId) {
        Claim claim = submittedClaim(id);
        claim.assignTo(staffId, NOW.minusSeconds(300));
        return claim;
    }
}
