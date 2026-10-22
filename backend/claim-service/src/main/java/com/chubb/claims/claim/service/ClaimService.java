package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.AdditionalInfoRequest;
import com.chubb.claims.claim.dto.ApproveClaimRequest;
import com.chubb.claims.claim.dto.ClaimDetailResponse;
import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.dto.ClaimStatisticsResponse;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.CreateClaimRequest;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.dto.RejectClaimRequest;
import com.chubb.claims.claim.dto.RequestInfoRequest;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.exception.NotAssignedOfficerException;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.ClaimSpecifications;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class ClaimService {

    /** Entity attributes clients may sort by; anything else is rejected rather than passed to the database layer. */
    static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "updatedAt", "claimNumber", "policyNumber", "status", "claimAmount", "incidentDate");

    private final ClaimRepository claimRepository;
    private final ClaimHistoryRepository claimHistoryRepository;
    private final ClaimEventPublisher eventPublisher;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final Clock clock;

    public ClaimService(ClaimRepository claimRepository,
                        ClaimHistoryRepository claimHistoryRepository,
                        ClaimEventPublisher eventPublisher,
                        ClaimNumberGenerator claimNumberGenerator,
                        Clock clock) {
        this.claimRepository = claimRepository;
        this.claimHistoryRepository = claimHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.claimNumberGenerator = claimNumberGenerator;
        this.clock = clock;
    }

    @Transactional
    public ClaimDetailResponse createClaim(CreateClaimRequest request, AuthenticatedUser user) {
        // One day of slack: a claimant east of the server's time zone can legitimately be on "tomorrow's" date.
        if (request.incidentDate().isAfter(LocalDate.now(clock).plusDays(1))) {
            throw new InvalidRequestException("incidentDate cannot be in the future");
        }

        Instant now = clock.instant();
        Claim claim = claimRepository.save(new Claim(
                claimNumberGenerator.next(),
                request.policyNumber().trim().toUpperCase(Locale.ROOT),
                user.userId(),
                user.fullName(),
                user.email(),
                request.claimType(),
                request.incidentDate(),
                trimToNull(request.incidentLocation()),
                request.description().trim(),
                request.claimAmount(),
                now));

        eventPublisher.publish(event(ClaimEventType.CLAIM_CREATED, claim, null, claim.getStatus(), user,
                "Claim submitted", now));

        return detail(claim);
    }

    @Transactional(readOnly = true)
    public ClaimDetailResponse getClaim(String claimNumber, AuthenticatedUser user) {
        Claim claim = claimRepository.findByClaimNumber(claimNumber.trim().toUpperCase(Locale.ROOT))
                .filter(found -> isVisibleTo(found, user))
                .orElseThrow(() -> new ClaimNotFoundException(claimNumber));
        return detail(claim);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> getMyClaims(AuthenticatedUser user, Pageable pageable) {
        Page<Claim> claims = claimRepository.findByClaimantId(user.userId(), sanitize(pageable));
        return PageResponse.from(claims.map(ClaimSummaryResponse::from));
    }

    /** Claimants only ever see their own claims; officers and admins search across all claims. */
    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> search(ClaimSearchCriteria criteria, AuthenticatedUser user, Pageable pageable) {
        if (criteria.fromDate() != null && criteria.toDate() != null && criteria.fromDate().isAfter(criteria.toDate())) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }

        Long restrictToClaimant = user.role() == Role.CLAIMANT ? user.userId() : null;
        Page<Claim> claims = claimRepository.findAll(
                ClaimSpecifications.matching(criteria, restrictToClaimant), sanitize(pageable));
        return PageResponse.from(claims.map(ClaimSummaryResponse::from));
    }

    /** The claimant's answer to an officer's request: MORE_INFO_REQUIRED -> UNDER_REVIEW. */
    @Transactional
    public ClaimDetailResponse submitAdditionalInfo(Long claimId, AdditionalInfoRequest request, AuthenticatedUser user) {
        Claim claim = claimRepository.findById(claimId)
                .filter(found -> found.getClaimantId().equals(user.userId()))
                .orElseThrow(() -> new ClaimNotFoundException(String.valueOf(claimId)));

        ClaimStatus before = claim.getStatus();
        if (before != ClaimStatus.MORE_INFO_REQUIRED) {
            throw new InvalidStatusTransitionException(
                    "Additional information can only be submitted while the claim is MORE_INFO_REQUIRED (current status: "
                            + before + ")");
        }

        Instant now = clock.instant();
        claim.transitionTo(ClaimStatus.UNDER_REVIEW, now);
        claimRepository.save(claim);

        eventPublisher.publish(event(ClaimEventType.ADDITIONAL_INFO_RECEIVED, claim, before, claim.getStatus(), user,
                request.message().trim(), now));

        return detail(claim);
    }

    // ---- officer workflow (called by assessment-service on behalf of an officer) ------------------------------

    @Transactional(readOnly = true)
    public ClaimSummaryResponse getClaimById(Long claimId) {
        return ClaimSummaryResponse.from(findById(claimId));
    }

    /** Raw totals per status and per officer; assessment-service derives dashboard metrics from them. */
    @Transactional(readOnly = true)
    public ClaimStatisticsResponse statistics() {
        return new ClaimStatisticsResponse(claimRepository.totalsByStatus(), claimRepository.totalsByOfficerAndStatus());
    }

    /** SUBMITTED -> UNDER_REVIEW, owned by the calling officer. */
    @Transactional
    public ClaimSummaryResponse assignToOfficer(Long claimId, AuthenticatedUser officer) {
        Claim claim = findById(claimId);
        ClaimStatus before = claim.getStatus();
        Instant now = clock.instant();

        claim.assignTo(officer.userId(), now);
        claimRepository.save(claim);

        String officerName = officer.fullName() != null ? officer.fullName() : "officer " + officer.userId();
        eventPublisher.publish(event(ClaimEventType.CLAIM_ASSIGNED, claim, before, claim.getStatus(), officer,
                "Assigned to " + officerName, now));
        return ClaimSummaryResponse.from(claim);
    }

    /** UNDER_REVIEW -> APPROVED, by the assigned officer. */
    @Transactional
    public ClaimSummaryResponse approve(Long claimId, ApproveClaimRequest request, AuthenticatedUser officer) {
        String comment = request == null ? null : trimToNull(request.comment());
        return decide(claimId, officer, ClaimStatus.APPROVED, ClaimEventType.CLAIM_APPROVED,
                comment != null ? comment : "Claim approved");
    }

    /** UNDER_REVIEW -> REJECTED, by the assigned officer. */
    @Transactional
    public ClaimSummaryResponse reject(Long claimId, RejectClaimRequest request, AuthenticatedUser officer) {
        return decide(claimId, officer, ClaimStatus.REJECTED, ClaimEventType.CLAIM_REJECTED, request.reason().trim());
    }

    /** UNDER_REVIEW -> MORE_INFO_REQUIRED, by the assigned officer. */
    @Transactional
    public ClaimSummaryResponse requestAdditionalInfo(Long claimId, RequestInfoRequest request, AuthenticatedUser officer) {
        return decide(claimId, officer, ClaimStatus.MORE_INFO_REQUIRED, ClaimEventType.ADDITIONAL_INFO_REQUESTED,
                request.message().trim());
    }

    private ClaimSummaryResponse decide(Long claimId, AuthenticatedUser officer, ClaimStatus target,
                                        ClaimEventType eventType, String details) {
        Claim claim = findById(claimId);
        if (!officer.userId().equals(claim.getAssignedOfficerId())) {
            throw new NotAssignedOfficerException();
        }

        ClaimStatus before = claim.getStatus();
        Instant now = clock.instant();
        claim.transitionTo(target, now);
        claimRepository.save(claim);

        eventPublisher.publish(event(eventType, claim, before, claim.getStatus(), officer, details, now));
        return ClaimSummaryResponse.from(claim);
    }

    private Claim findById(Long claimId) {
        return claimRepository.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(String.valueOf(claimId)));
    }

    private ClaimDetailResponse detail(Claim claim) {
        return ClaimDetailResponse.from(claim, claimHistoryRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId()));
    }

    private static boolean isVisibleTo(Claim claim, AuthenticatedUser user) {
        return user.role() != Role.CLAIMANT || claim.getClaimantId().equals(user.userId());
    }

    private static ClaimEvent event(ClaimEventType type, Claim claim, ClaimStatus from, ClaimStatus to,
                                    AuthenticatedUser actor, String details, Instant now) {
        return new ClaimEvent(type, claim.getId(), claim.getClaimNumber(), claim.getClaimantId(),
                claim.getAssignedOfficerId(), from, to, actor.userId(), actor.role().name(), details, now);
    }

    private static Pageable sanitize(Pageable pageable) {
        return PageableSanitizer.sanitize(pageable, SORTABLE_FIELDS);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
