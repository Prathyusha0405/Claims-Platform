package com.chubb.claims.assessment.client;

import org.springframework.data.domain.Pageable;

/**
 * Everything assessment-service needs from claim-service, which owns claims and their lifecycle. Calls run on behalf
 * of the current officer or admin: their bearer token is forwarded, so claim-service makes the authorization call.
 * <p>
 * Failures surface as the exceptions in {@code com.chubb.claims.assessment.exception}: a missing claim, a conflicting
 * state, an officer acting on someone else's claim, or claim-service itself being unavailable.
 */
public interface ClaimServiceClient {

    /** Claims waiting for an officer (status SUBMITTED), paged and sorted by claim-service. */
    RemotePage<RemoteClaim> findUnassigned(Pageable pageable);

    RemoteClaim getClaim(Long claimId);

    /** SUBMITTED -> UNDER_REVIEW, assigned to the calling officer. */
    RemoteClaim assign(Long claimId);

    /** UNDER_REVIEW -> APPROVED. {@code comment} may be null. */
    RemoteClaim approve(Long claimId, String comment);

    /** UNDER_REVIEW -> REJECTED. */
    RemoteClaim reject(Long claimId, String reason);

    /** UNDER_REVIEW -> MORE_INFO_REQUIRED. */
    RemoteClaim requestInfo(Long claimId, String message);

    RemoteStatistics getStatistics();
}
