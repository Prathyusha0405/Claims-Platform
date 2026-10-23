package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketExposure;
import com.chubb.claims.assessment.dto.StatusExposure;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Liability exposure: {@code SUM(claimAmount) WHERE status != SETTLED}.
 * <p>
 * Claim-service does the heavy lifting (one GROUP BY over its table) and hands over totals per market and status, each in
 * its market's own currency. The business rule lives here, in one place, as a pure function of those totals and the rate
 * card: every (market, status) total is converted on its own, rounded to cents, and the figures on screen are sums of those
 * rounded parts, so a breakdown always adds up to its total.
 */
@Component
public class LiabilityExposureCalculator {

    public static final String FORMULA = "SUM(claimAmount) WHERE status != SETTLED";

    private static final int MONEY_SCALE = 2;

    private final ExchangeRates exchangeRates;

    public LiabilityExposureCalculator(ExchangeRates exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    /**
     * @param market the market the headline figures are limited to, or null for every market
     * @param totals every (market, status) total claim-service reported; {@code byMarket} always covers all of them
     */
    public LiabilityExposureResponse calculate(String market, List<RemoteStatusTotal> totals, Instant generatedAt) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        Map<ClaimStatus, BigDecimal> amounts = new EnumMap<>(ClaimStatus.class);
        for (RemoteStatusTotal total : totals) {
            if (market != null && !market.equals(total.market())) {
                continue;
            }
            counts.merge(total.status(), total.claimCount(), Long::sum);
            amounts.merge(total.status(), exchangeRates.toReportingCurrency(total.currency(), total.totalAmount()), BigDecimal::add);
        }

        BigDecimal exposure = zero();
        BigDecimal excluded = zero();
        long exposedClaims = 0;
        List<StatusExposure> byStatus = new ArrayList<>();

        for (ClaimStatus status : ClaimStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            BigDecimal amount = amounts.getOrDefault(status, BigDecimal.ZERO).setScale(MONEY_SCALE);
            boolean included = status.countsTowardLiability();

            if (included) {
                exposure = exposure.add(amount);
                exposedClaims += count;
            } else {
                excluded = excluded.add(amount);
            }
            byStatus.add(new StatusExposure(status, count, amount, included));
        }

        return new LiabilityExposureResponse(exchangeRates.reportingCurrency(), market, exposure, exposedClaims, excluded,
                FORMULA, List.copyOf(byStatus), byMarket(totals), generatedAt);
    }

    /** One row per market that has claims (even if all are settled), in country-code order. */
    private List<MarketExposure> byMarket(List<RemoteStatusTotal> totals) {
        Map<String, Accumulator> markets = new TreeMap<>();
        for (RemoteStatusTotal total : totals) {
            Accumulator accumulator = markets.computeIfAbsent(total.market(), code -> new Accumulator(total.currency()));
            if (total.status().countsTowardLiability()) {
                accumulator.add(total, exchangeRates.toReportingCurrency(total.currency(), total.totalAmount()));
            }
        }
        List<MarketExposure> rows = new ArrayList<>();
        markets.forEach((code, accumulator) -> rows.add(new MarketExposure(code, accumulator.currency,
                exchangeRates.rate(accumulator.currency), accumulator.claims, accumulator.nativeAmount, accumulator.reporting)));
        return List.copyOf(rows);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static final class Accumulator {
        private final String currency;
        private long claims;
        private BigDecimal nativeAmount = zero();
        private BigDecimal reporting = zero();

        private Accumulator(String currency) {
            this.currency = currency;
        }

        private void add(RemoteStatusTotal total, BigDecimal converted) {
            claims += total.claimCount();
            if (total.totalAmount() != null) {
                nativeAmount = nativeAmount.add(total.totalAmount());
            }
            reporting = reporting.add(converted);
        }
    }
}
