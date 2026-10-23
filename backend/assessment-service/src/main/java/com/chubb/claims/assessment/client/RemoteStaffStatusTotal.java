package com.chubb.claims.assessment.client;

import java.math.BigDecimal;

/** Claim-service's aggregate for one staff member in one market and status (assigned claims only), in the market's currency. */
public record RemoteStaffStatusTotal(Long staffId, String market, String currency, ClaimStatus status, long claimCount,
                                       BigDecimal totalAmount) {
}
