package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;

import java.time.Instant;

/** One claim reduced to what a trend needs: when it was submitted and where it stands. */
public record TrendRow(Instant createdAt, ClaimStatus status) {
}
