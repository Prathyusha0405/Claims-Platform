package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.StatisticsCriteria;
import com.chubb.claims.claim.dto.TrendCriteria;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.exception.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Which claims an aggregate query looks at, in the form the queries bind: a half-open time window and optional market and
 * type. An absent bound is a very early or very late instant and an absent market or type is a flag plus an ignored
 * placeholder, so the queries never compare a null parameter.
 *
 * @param from        inclusive start of the window
 * @param toExclusive exclusive end of the window
 */
public record ClaimScope(Instant from, Instant toExclusive, Market market, ClaimType claimType) {

    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    /** Every claim. */
    public static ClaimScope all() {
        return new ClaimScope(EARLIEST, LATEST, null, null);
    }

    public static ClaimScope of(StatisticsCriteria criteria) {
        return of(null, criteria.claimType(), criteria.fromDate(), criteria.toDate());
    }

    public static ClaimScope of(TrendCriteria criteria) {
        return of(criteria.market(), criteria.claimType(), criteria.fromDate(), criteria.toDate());
    }

    /** Dates are submission dates in UTC and both inclusive, exactly as in claim search. */
    public static ClaimScope of(Market market, ClaimType claimType, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
        Instant from = fromDate == null ? EARLIEST : fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = toDate == null ? LATEST : toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new ClaimScope(from, toExclusive, market, claimType);
    }

    /** The same window and market with the type left open, for breakdowns that must keep showing every type. */
    public ClaimScope withAnyType() {
        return new ClaimScope(from, toExclusive, market, null);
    }

    boolean anyMarket() {
        return market == null;
    }

    Market marketOrPlaceholder() {
        return market == null ? Market.values()[0] : market;
    }

    boolean anyType() {
        return claimType == null;
    }

    ClaimType typeOrPlaceholder() {
        return claimType == null ? ClaimType.values()[0] : claimType;
    }
}
