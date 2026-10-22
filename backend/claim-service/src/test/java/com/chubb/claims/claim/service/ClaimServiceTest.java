package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.AdditionalInfoRequest;
import com.chubb.claims.claim.dto.ClaimDetailResponse;
import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.CreateClaimRequest;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final AuthenticatedUser JANE = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);
    private static final AuthenticatedUser OTHER_CLAIMANT = new AuthenticatedUser(6L, "bob@example.com", "Bob Lim", Role.CLAIMANT);
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);

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
        claimService = new ClaimService(claimRepository, claimHistoryRepository, eventPublisher, claimNumberGenerator, CLOCK);
    }

    // ---- createClaim ------------------------------------------------------------------------------------------

    @Test
    void createClaimSavesASubmittedClaimAndPublishesClaimCreated() {
        when(claimNumberGenerator.next()).thenReturn("CLM-2026-000001");
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> {
            Claim saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        ClaimDetailResponse response = claimService.createClaim(new CreateClaimRequest(
                " pol-2026-1 ", ClaimType.AUTO, LocalDate.of(2026, 10, 15), "  Orchard Road ", "  Rear-ended  ",
                new BigDecimal("2500.00")), JANE);

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(saved.capture());
        Claim claim = saved.getValue();
        assertThat(claim.getClaimNumber()).isEqualTo("CLM-2026-000001");
        assertThat(claim.getPolicyNumber()).isEqualTo("POL-2026-1");
        assertThat(claim.getClaimantId()).isEqualTo(5L);
        assertThat(claim.getClaimantName()).isEqualTo("Jane Tan");
        assertThat(claim.getClaimantEmail()).isEqualTo("jane@example.com");
        assertThat(claim.getIncidentLocation()).isEqualTo("Orchard Road");
        assertThat(claim.getDescription()).isEqualTo("Rear-ended");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getCreatedAt()).isEqualTo(NOW);
        assertThat(claim.getAssignedOfficerId()).isNull();

        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000001");
        assertThat(response.status()).isEqualTo(ClaimStatus.SUBMITTED);

        ArgumentCaptor<ClaimEvent> event = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new ClaimEvent(ClaimEventType.CLAIM_CREATED, 10L, "CLM-2026-000001", 5L,
                null, null, ClaimStatus.SUBMITTED, 5L, "CLAIMANT", "Claim submitted", NOW));
    }

    @Test
    void createClaimStoresBlankLocationAsNull() {
        when(claimNumberGenerator.next()).thenReturn("CLM-2026-000002");
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        claimService.createClaim(new CreateClaimRequest("POL-1234", ClaimType.HOME, LocalDate.of(2026, 10, 1), "   ",
                "Burst pipe", new BigDecimal("10.00")), JANE);

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(saved.capture());
        assertThat(saved.getValue().getIncidentLocation()).isNull();
    }

    @Test
    void createClaimRejectsAnIncidentDateInTheFuture() {
        CreateClaimRequest request = new CreateClaimRequest("POL-1234", ClaimType.AUTO, LocalDate.of(2026, 10, 24), null,
                "desc", new BigDecimal("10.00"));

        assertThatThrownBy(() -> claimService.createClaim(request, JANE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future");

        verifyNoInteractions(claimRepository, eventPublisher, claimNumberGenerator);
    }

    @Test
    void createClaimToleratesTomorrowForClaimantsEastOfTheServer() {
        when(claimNumberGenerator.next()).thenReturn("CLM-2026-000003");
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // "today" is 2026-10-22 in UTC; a claimant in Sydney can already be on the 23rd.
        claimService.createClaim(new CreateClaimRequest("POL-1234", ClaimType.TRAVEL, LocalDate.of(2026, 10, 23), null,
                "desc", new BigDecimal("10.00")), JANE);

        verify(claimRepository).save(any(Claim.class));
    }

    // ---- getClaim ---------------------------------------------------------------------------------------------

    @Test
    void getClaimReturnsTheClaimantsOwnClaimWithItsHistory() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        when(claimRepository.findByClaimNumber("CLM-2026-000001")).thenReturn(Optional.of(claim));

        ClaimDetailResponse response = claimService.getClaim("CLM-2026-000001", JANE);

        assertThat(response.id()).isEqualTo(10L);
        verify(claimHistoryRepository).findByClaimIdOrderByCreatedAtAscIdAsc(10L);
    }

    @Test
    void getClaimNormalizesTheClaimNumberBeforeLookup() {
        when(claimRepository.findByClaimNumber("CLM-2026-000001")).thenReturn(Optional.of(claim(10L, "CLM-2026-000001", 5L)));

        assertThat(claimService.getClaim("  clm-2026-000001 ", JANE).claimNumber()).isEqualTo("CLM-2026-000001");
    }

    @Test
    void getClaimHidesAnotherClaimantsClaimAsNotFound() {
        when(claimRepository.findByClaimNumber("CLM-2026-000001")).thenReturn(Optional.of(claim(10L, "CLM-2026-000001", 5L)));

        assertThatThrownBy(() -> claimService.getClaim("CLM-2026-000001", OTHER_CLAIMANT))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void getClaimLetsOfficersOpenAnyClaim() {
        when(claimRepository.findByClaimNumber("CLM-2026-000001")).thenReturn(Optional.of(claim(10L, "CLM-2026-000001", 5L)));

        assertThat(claimService.getClaim("CLM-2026-000001", OFFICER).claimantId()).isEqualTo(5L);
    }

    @Test
    void getClaimThrowsWhenTheClaimDoesNotExist() {
        when(claimRepository.findByClaimNumber("CLM-2026-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaim("CLM-2026-999999", OFFICER))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining("CLM-2026-999999");
    }

    // ---- getMyClaims / search: paging and sorting -------------------------------------------------------------

    @Test
    void getMyClaimsDefaultsToNewestFirstWithADeterministicTieBreaker() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        when(claimRepository.findByClaimantId(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(2, 10), 31));

        PageResponse<ClaimSummaryResponse> page = claimService.getMyClaims(JANE, PageRequest.of(2, 10));

        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(claimRepository).findByClaimantId(eq(5L), used.capture());
        assertThat(used.getValue().getPageNumber()).isEqualTo(2);
        assertThat(used.getValue().getPageSize()).isEqualTo(10);
        assertThat(used.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        assertThat(page.content()).extracting(ClaimSummaryResponse::claimNumber).containsExactly("CLM-2026-000001");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(31);
        assertThat(page.totalPages()).isEqualTo(4);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isFalse();
    }

    @Test
    void requestedSortIsKeptAndTieBrokenById() {
        when(claimRepository.findByClaimantId(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        claimService.getMyClaims(JANE, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "claimAmount")));

        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(claimRepository).findByClaimantId(eq(5L), used.capture());
        assertThat(used.getValue().getSort()).containsExactly(Sort.Order.asc("claimAmount"), Sort.Order.asc("id"));
    }

    @Test
    void unsupportedSortFieldIsRejectedBeforeReachingTheDatabase() {
        Pageable bySecret = PageRequest.of(0, 20, Sort.by("claimantEmail"));

        assertThatThrownBy(() -> claimService.getMyClaims(JANE, bySecret))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("claimantEmail")
                .hasMessageContaining("createdAt");

        verifyNoInteractions(claimRepository);
    }

    @Test
    void searchRejectsAnInvertedDateRange() {
        ClaimSearchCriteria inverted = new ClaimSearchCriteria(null, null, null,
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), null);

        assertThatThrownBy(() -> claimService.search(inverted, OFFICER, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("fromDate");

        verifyNoInteractions(claimRepository);
    }

    @Test
    void searchValidatesSortFieldsToo() {
        ClaimSearchCriteria none = new ClaimSearchCriteria(null, null, null, null, null, null);

        assertThatThrownBy(() -> claimService.search(none, OFFICER, PageRequest.of(0, 20, Sort.by("version"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchReturnsAMappedPage() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        when(claimRepository.findAll(ArgumentMatchers.<Specification<Claim>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(0, 20), 1));

        PageResponse<ClaimSummaryResponse> page = claimService.search(
                new ClaimSearchCriteria("CLM", null, ClaimStatus.SUBMITTED, null, null, null), OFFICER, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    // ---- submitAdditionalInfo ---------------------------------------------------------------------------------

    @Test
    void additionalInfoMovesTheClaimBackUnderReviewAndPublishesTheEvent() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        claim.transitionTo(ClaimStatus.UNDER_REVIEW, NOW.minusSeconds(60));
        claim.transitionTo(ClaimStatus.MORE_INFO_REQUIRED, NOW.minusSeconds(30));
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimDetailResponse response = claimService.submitAdditionalInfo(10L, new AdditionalInfoRequest("  Estimate attached  "), JANE);

        assertThat(response.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getUpdatedAt()).isEqualTo(NOW);
        verify(claimRepository).save(claim);

        ArgumentCaptor<ClaimEvent> event = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ClaimEventType.ADDITIONAL_INFO_RECEIVED);
        assertThat(event.getValue().fromStatus()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        assertThat(event.getValue().toStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(event.getValue().details()).isEqualTo("Estimate attached");
        assertThat(event.getValue().actorId()).isEqualTo(5L);
    }

    @Test
    void additionalInfoOnSomeoneElsesClaimIsNotFoundAndChangesNothing() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        claim.transitionTo(ClaimStatus.UNDER_REVIEW, NOW);
        claim.transitionTo(ClaimStatus.MORE_INFO_REQUIRED, NOW);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.submitAdditionalInfo(10L, new AdditionalInfoRequest("hi"), OTHER_CLAIMANT))
                .isInstanceOf(ClaimNotFoundException.class);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        verify(claimRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void additionalInfoIsRefusedUnlessTheClaimIsAwaitingIt() {
        Claim claim = claim(10L, "CLM-2026-000001", 5L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.submitAdditionalInfo(10L, new AdditionalInfoRequest("hi"), JANE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("MORE_INFO_REQUIRED")
                .hasMessageContaining("SUBMITTED");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        verify(claimRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void additionalInfoForAnUnknownClaimIsNotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.submitAdditionalInfo(99L, new AdditionalInfoRequest("hi"), JANE))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    private static Claim claim(Long id, String claimNumber, Long claimantId) {
        Claim claim = new Claim(claimNumber, "POL-2026-1", claimantId, "Jane Tan", "jane@example.com", ClaimType.AUTO,
                LocalDate.of(2026, 10, 1), "Orchard Road", "Rear-ended", new BigDecimal("2500.00"), NOW.minusSeconds(120));
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }
}
