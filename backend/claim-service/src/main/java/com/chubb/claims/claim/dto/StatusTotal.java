package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;

import java.math.BigDecimal;

/**
 * How many claims a market has in a status and what they add up to, in that market's own currency. Amounts in different
 * currencies must never be added together, which is why the currency travels with every row.
 */
public record StatusTotal(Market market, Currency currency, ClaimStatus status, long claimCount, BigDecimal totalAmount) {
}
