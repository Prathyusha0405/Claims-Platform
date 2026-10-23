package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.ClaimType;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteTypeTotal;
import com.chubb.claims.assessment.client.StatisticsScope;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.ClaimTypeExposure;
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
     * @param scope      what the figures are limited to; the market narrows the headline figures only
     * @param statistics every total claim-service reported for the date range and claim type; {@code byMarket} always covers
     *                   every market and {@code byClaimType} every claim type
     */
    public LiabilityExposureResponse calculate(StatisticsScope scope, RemoteStatistics statistics, Instant generatedAt) {
        String market = scope.market() == null ? null : scope.market().name();
        List<RemoteStatusTotal> totals = statistics.byStatus();
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

        return new LiabilityExposureResponse(exchangeRates.reportingCurrency(), market, scope.claimType(), scope.fromDate(),
                scope.toDate(), exposure, exposedClaims, excluded, FORMULA, List.copyOf(byStatus), byMarket(totals),
                byClaimType(market, statistics.byType()), generatedAt);
    }

    /**
     * One row per claim type that has claims (even if all are settled), in the order of the claim types. Each (market, type,
     * status) total is converted and rounded on its own, like the status breakdown, so the figures are whole sums of rounded
     * parts; they can differ from the same type's share of the headline by a few cents, because the headline is rounded per
     * (market, status) instead.
     */
    private List<ClaimTypeExposure> byClaimType(String market, List<RemoteTypeTotal> typeTotals) {
        Map<ClaimType, TypeAccumulator> types = new EnumMap<>(ClaimType.class);
        for (RemoteTypeTotal total : typeTotals) {
            if (market != null && !market.equals(total.market())) {
                continue;
            }
            TypeAccumulator accumulator = types.computeIfAbsent(total.claimType(), type -> new TypeAccumulator());
            if (total.status().countsTowardLiability()) {
                accumulator.claims += total.claimCount();
                accumulator.reporting = accumulator.reporting.add(
                        exchangeRates.toReportingCurrency(total.currency(), total.totalAmount()));
            }
        }
        List<ClaimTypeExposure> rows = new ArrayList<>();
        types.forEach((type, accumulator) -> rows.add(new ClaimTypeExposure(type, accumulator.claims, accumulator.reporting)));
        return List.copyOf(rows);
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

    private static final class TypeAccumulator {
        private long claims;
        private BigDecimal reporting = zero();
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
