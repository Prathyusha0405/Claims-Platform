package com.chubb.claims.claim.event;

import java.util.List;

/**
 * Who the claims staff are. Users live in auth-service, so claim-service needs a way to ask when an event has to reach
 * every staff member (a new claim waiting in the work queue). Kept behind an interface, like the event publisher, so a
 * real directory can replace {@link HistoryClaimsStaffDirectory} without touching the publisher or the factory.
 */
public interface ClaimsStaffDirectory {

    /** User ids of the claims staff, in ascending order; empty when none are known. */
    List<Long> claimsStaffIds();
}
