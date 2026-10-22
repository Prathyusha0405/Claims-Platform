package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;

import java.math.BigDecimal;

/** How many claims are in a status and what they add up to. */
public record StatusTotal(ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
