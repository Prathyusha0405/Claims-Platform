package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;

import java.math.BigDecimal;

/**
 * How many claims of one type a market has in a status and what they add up to, in that market's own currency. The currency
 * travels with every row because amounts in different currencies must never be added together.
 */
public record TypeStatusTotal(Market market, Currency currency, ClaimType claimType, ClaimStatus status, long claimCount,
                              BigDecimal totalAmount) {
}
