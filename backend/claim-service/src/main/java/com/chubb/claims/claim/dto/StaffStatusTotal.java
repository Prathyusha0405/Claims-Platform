package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;

import java.math.BigDecimal;

/** Claims assigned to one staff member, in one market and status, with their total in that market's own currency. */
public record StaffStatusTotal(Long staffId, Market market, Currency currency, ClaimStatus status, long claimCount,
                                 BigDecimal totalAmount) {
}
