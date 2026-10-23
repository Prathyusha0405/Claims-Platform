package com.chubb.claims.assessment.client;

import org.springframework.data.domain.Pageable;

/**
 * Everything assessment-service needs from claim-service, which owns claims and their lifecycle. Calls run on behalf
 * of the current staff member or manager: their bearer token is forwarded, so claim-service makes the authorization call.
 * <p>
 * Failures surface as the exceptions in {@code com.chubb.claims.assessment.exception}: a missing claim, a conflicting
 * state, a staff member acting on someone else's claim, or claim-service itself being unavailable.
 */
public interface ClaimServiceClient {

    /**
     * Claims waiting for a staff member (status SUBMITTED), paged and sorted by claim-service.
     *
     * @param market optional ISO country code to narrow the queue to one market; null for every market
     */
    RemotePage<RemoteClaim> findUnassigned(String market, Pageable pageable);

    RemoteClaim getClaim(Long claimId);

    /** SUBMITTED -> UNDER_REVIEW, assigned to the calling staff member. */
    RemoteClaim assign(Long claimId);

    /** UNDER_REVIEW -> APPROVED. {@code comment} may be null. */
    RemoteClaim approve(Long claimId, String comment);

    /** UNDER_REVIEW -> REJECTED. */
    RemoteClaim reject(Long claimId, String reason);

    /** UNDER_REVIEW -> MORE_INFO_REQUIRED. */
    RemoteClaim requestInfo(Long claimId, String message);

    RemoteStatistics getStatistics();
}
