package com.chubb.claims.assessment.client;

import java.math.BigDecimal;

/** Claim-service's per-status aggregate: how many claims, and what they add up to. */
public record RemoteStatusTotal(ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
