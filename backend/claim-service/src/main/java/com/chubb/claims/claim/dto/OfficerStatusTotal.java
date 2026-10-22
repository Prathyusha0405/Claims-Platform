package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;

import java.math.BigDecimal;

/** Claims assigned to one officer, in one status. */
public record OfficerStatusTotal(Long officerId, ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
