package com.chubb.claims.assessment.client;

import java.math.BigDecimal;

/** Claim-service's aggregate for one officer in one status (assigned claims only). */
public record RemoteOfficerStatusTotal(Long officerId, ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
