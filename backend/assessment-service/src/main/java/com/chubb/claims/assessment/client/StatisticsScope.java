package com.chubb.claims.assessment.client;

import com.chubb.claims.assessment.exception.InvalidRequestException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * Which claims a dashboard looks at: every claim, or those in one market, of one type, submitted within a date range (UTC,
 * both ends inclusive). Every field is optional. Built from the dashboard's query parameters, which is where an unknown
 * market or type, or a range that ends before it starts, is turned into a client error before claim-service is asked.
 * <p>
 * The market is applied here and not by claim-service for the headline figures, because the per-market comparison must keep
 * showing every market; only the type and the dates travel to claim-service's statistics.
 */
public record StatisticsScope(Market market, ClaimType claimType, LocalDate fromDate, LocalDate toDate) {

    public static StatisticsScope all() {
        return new StatisticsScope(null, null, null, null);
    }

    /** The scope for just a market code (null or blank for every market), as the first dashboards took it. */
    public static StatisticsScope ofMarket(String marketCode) {
        return of(marketCode, null, null, null);
    }

    /** Null or blank text means "not filtered"; anything that is not a known value is a client error. */
    public static StatisticsScope of(String marketCode, String claimTypeCode, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
        return new StatisticsScope(parse(Market.class, "market", marketCode), parse(ClaimType.class, "claimType", claimTypeCode),
                fromDate, toDate);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidRequestException("Unknown " + name + " '" + code.trim() + "'; allowed values: "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }
}
