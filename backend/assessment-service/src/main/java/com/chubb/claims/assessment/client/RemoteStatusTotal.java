package com.chubb.claims.assessment.client;

import java.math.BigDecimal;

/**
 * Claim-service's aggregate for one market in one status: how many claims, and what they add up to in that market's own
 * currency. Amounts of different currencies must be converted before they are added.
 */
public record RemoteStatusTotal(String market, String currency, ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
