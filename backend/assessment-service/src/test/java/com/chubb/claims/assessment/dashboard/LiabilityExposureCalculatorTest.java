package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.config.DashboardProperties;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketExposure;
import com.chubb.claims.assessment.dto.StatusExposure;
import com.chubb.claims.assessment.service.ExchangeRates;
import com.chubb.claims.assessment.service.LiabilityExposureCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md: Liability Exposure = SUM(claimAmount) WHERE status != SETTLED. Claims are in their market's own currency, so
 * the sum is taken in the reporting currency (USD) after converting each market's totals at the configured rates.
 */
class LiabilityExposureCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private static final ExchangeRates RATES = new ExchangeRates(new DashboardProperties("USD",
            Map.of("SGD", new BigDecimal("1.35"), "JPY", new BigDecimal("150"), "AUD", new BigDecimal("1.50"))));

    private final LiabilityExposureCalculator calculator = new LiabilityExposureCalculator(RATES);

    // ---- the formula ------------------------------------------------------------------------------------------

    @Test
    void sumsEveryStatusExceptSettled() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                total(ClaimStatus.SUBMITTED, 3, "300.00"),
                total(ClaimStatus.UNDER_REVIEW, 2, "2000.00"),
                total(ClaimStatus.MORE_INFO_REQUIRED, 1, "500.50"),
                total(ClaimStatus.APPROVED, 4, "4000.00"),
                total(ClaimStatus.REJECTED, 1, "100.00"),
                total(ClaimStatus.SETTLED, 2, "900.00")), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("6900.50");
        assertThat(result.exposedClaims()).isEqualTo(11);
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("900.00");
        assertThat(result.formula()).isEqualTo("SUM(claimAmount) WHERE status != SETTLED");
        assertThat(result.reportingCurrency()).isEqualTo("USD");
        assertThat(result.market()).isNull();
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void settledClaimsNeverCountTowardsExposure() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(total(ClaimStatus.SETTLED, 5, "12345.67")), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("0.00");
        assertThat(result.exposedClaims()).isZero();
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("12345.67");
    }

    @Test
    void noClaimsMeansZeroExposure() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("0.00");
        assertThat(result.totalLiabilityExposure().scale()).isEqualTo(2);
        assertThat(result.exposedClaims()).isZero();
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(result.byMarket()).isEmpty();
    }

    @Test
    void listsEveryStatusInLifecycleOrderWithZerosWhereThereAreNoClaims() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(total(ClaimStatus.APPROVED, 1, "10.00")), NOW);

        assertThat(result.byStatus()).extracting(StatusExposure::status).containsExactly(ClaimStatus.values());
        assertThat(result.byStatus()).filteredOn(entry -> entry.status() != ClaimStatus.APPROVED)
                .allSatisfy(entry -> {
                    assertThat(entry.claimCount()).isZero();
                    assertThat(entry.amount()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void flagsWhichStatusesTheFormulaIncludes() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(), NOW);

        assertThat(result.byStatus()).filteredOn(StatusExposure::includedInExposure)
                .extracting(StatusExposure::status)
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED,
                        ClaimStatus.APPROVED, ClaimStatus.REJECTED);
        assertThat(result.byStatus()).filteredOn(entry -> !entry.includedInExposure())
                .extracting(StatusExposure::status).containsExactly(ClaimStatus.SETTLED);
    }

    @Test
    void mergesDuplicateStatusRowsAndToleratesNullAmounts() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                total(ClaimStatus.SUBMITTED, 1, "100.00"),
                total(ClaimStatus.SUBMITTED, 2, "50.25"),
                new RemoteStatusTotal("JP", "JPY", ClaimStatus.APPROVED, 1, null)), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("150.25");
        assertThat(result.exposedClaims()).isEqualTo(4);
        assertThat(result.byStatus().get(0)).isEqualTo(new StatusExposure(ClaimStatus.SUBMITTED, 3, new BigDecimal("150.25"), true));
    }

    @Test
    void doesNotLoseCentsToFloatingPointRounding() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                total(ClaimStatus.SUBMITTED, 1, "0.10"),
                total(ClaimStatus.UNDER_REVIEW, 1, "0.20")), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualTo(new BigDecimal("0.30"));
    }

    // ---- currencies and markets -------------------------------------------------------------------------------

    @Test
    void convertsEachMarketsTotalsToTheReportingCurrencyBeforeAddingThem() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                row("SG", "SGD", ClaimStatus.SUBMITTED, 2, "270.00"),         // 200.00 USD
                row("JP", "JPY", ClaimStatus.SUBMITTED, 1, "15000.00"),       // 100.00 USD
                row("AU", "AUD", ClaimStatus.APPROVED, 1, "3000.00"),         // 2000.00 USD
                row("SG", "SGD", ClaimStatus.SETTLED, 1, "135.00")), NOW);    // 100.00 USD, excluded

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("2300.00");
        assertThat(result.exposedClaims()).isEqualTo(4);
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("100.00");
        assertThat(result.byStatus().get(0).amount()).isEqualByComparingTo("300.00");   // SUBMITTED: 200.00 + 100.00
    }

    @Test
    void roundsEachMarketAndStatusOnItsOwnSoEveryBreakdownAddsUpToItsTotal() {
        // 1,000 JPY is 6.666... USD. Two statuses of 1,000 JPY each are 6.67 + 6.67 = 13.34 (not 13.33), because the
        // rounded parts are what is shown, and the total must be the sum of the parts.
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                row("JP", "JPY", ClaimStatus.SUBMITTED, 1, "1000.00"),
                row("JP", "JPY", ClaimStatus.UNDER_REVIEW, 1, "1000.00")), NOW);

        assertThat(result.byStatus().get(0).amount()).isEqualByComparingTo("6.67");
        assertThat(result.byStatus().get(1).amount()).isEqualByComparingTo("6.67");
        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("13.34");
        assertThat(result.byMarket()).singleElement().satisfies(market ->
                assertThat(market.exposureInReportingCurrency()).isEqualByComparingTo("13.34"));
    }

    @Test
    void listsEveryMarketInItsOwnCurrencyAndInTheReportingCurrency() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(
                row("SG", "SGD", ClaimStatus.SUBMITTED, 2, "270.00"),
                row("JP", "JPY", ClaimStatus.SUBMITTED, 1, "15000.00"),
                row("JP", "JPY", ClaimStatus.APPROVED, 1, "150000.00"),
                row("SG", "SGD", ClaimStatus.SETTLED, 1, "135.00")), NOW);

        assertThat(result.byMarket()).extracting(MarketExposure::market).containsExactly("JP", "SG");   // country-code order
        MarketExposure japan = result.byMarket().get(0);
        assertThat(japan.currency()).isEqualTo("JPY");
        assertThat(japan.rate()).isEqualByComparingTo("150");
        assertThat(japan.claimCount()).isEqualTo(2);
        assertThat(japan.exposure()).isEqualByComparingTo("165000.00");
        assertThat(japan.exposureInReportingCurrency()).isEqualByComparingTo("1100.00");
        MarketExposure singapore = result.byMarket().get(1);
        assertThat(singapore.currency()).isEqualTo("SGD");
        assertThat(singapore.rate()).isEqualByComparingTo("1.35");
        assertThat(singapore.claimCount()).isEqualTo(2);                       // the settled claim is not exposed
        assertThat(singapore.exposure()).isEqualByComparingTo("270.00");
        assertThat(singapore.exposureInReportingCurrency()).isEqualByComparingTo("200.00");
    }

    @Test
    void aMarketFilterNarrowsTheHeadlineFiguresButNotTheMarketBreakdown() {
        List<RemoteStatusTotal> totals = List.of(
                row("SG", "SGD", ClaimStatus.SUBMITTED, 2, "270.00"),
                row("JP", "JPY", ClaimStatus.SUBMITTED, 1, "15000.00"),
                row("JP", "JPY", ClaimStatus.SETTLED, 1, "30000.00"));

        LiabilityExposureResponse result = calculator.calculate("JP", totals, NOW);

        assertThat(result.market()).isEqualTo("JP");
        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("100.00");
        assertThat(result.exposedClaims()).isEqualTo(1);
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("200.00");
        assertThat(result.byStatus().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(result.byMarket()).extracting(MarketExposure::market).containsExactly("JP", "SG");
    }

    @Test
    void aMarketWithOnlySettledClaimsStillAppearsWithNoExposure() {
        LiabilityExposureResponse result = calculator.calculate(null, List.of(row("AU", "AUD", ClaimStatus.SETTLED, 3, "4500.00")), NOW);

        assertThat(result.byMarket()).singleElement().satisfies(market -> {
            assertThat(market.market()).isEqualTo("AU");
            assertThat(market.claimCount()).isZero();
            assertThat(market.exposure()).isEqualByComparingTo("0.00");
            assertThat(market.exposureInReportingCurrency()).isEqualByComparingTo("0.00");
        });
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void aCurrencyWithoutAnExchangeRateIsAnErrorNotAGuess() {
        assertThatThrownBy(() -> calculator.calculate(null, List.of(row("MY", "MYR", ClaimStatus.SUBMITTED, 1, "10.00")), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MYR");
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    /** A Japanese total given in USD terms: the yen amount is 150 times larger, so it converts back to exactly {@code usd}. */
    private static RemoteStatusTotal total(ClaimStatus status, long count, String usd) {
        return new RemoteStatusTotal("JP", "JPY", status, count, new BigDecimal(usd).multiply(new BigDecimal("150")));
    }

    private static RemoteStatusTotal row(String market, String currency, ClaimStatus status, long count, String amount) {
        return new RemoteStatusTotal(market, currency, status, count, new BigDecimal(amount));
    }
}
