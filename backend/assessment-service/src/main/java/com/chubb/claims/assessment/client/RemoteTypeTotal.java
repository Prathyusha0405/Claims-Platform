package com.chubb.claims.assessment.client;

import java.math.BigDecimal;

/**
 * Claim-service's aggregate for one claim type in one market and status: how many claims, and what they add up to in that
 * market's own currency. Amounts of different currencies must be converted before they are added.
 */
public record RemoteTypeTotal(String market, String currency, ClaimType claimType, ClaimStatus status, long claimCount,
                              BigDecimal totalAmount) {
}
