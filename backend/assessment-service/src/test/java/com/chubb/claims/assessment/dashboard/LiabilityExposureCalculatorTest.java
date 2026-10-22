package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.StatusExposure;
import com.chubb.claims.assessment.service.LiabilityExposureCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CLAUDE.md: Liability Exposure = SUM(claimAmount) WHERE status != SETTLED. */
class LiabilityExposureCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private final LiabilityExposureCalculator calculator = new LiabilityExposureCalculator();

    @Test
    void sumsEveryStatusExceptSettled() {
        LiabilityExposureResponse result = calculator.calculate(List.of(
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
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void settledClaimsNeverCountTowardsExposure() {
        LiabilityExposureResponse result = calculator.calculate(List.of(total(ClaimStatus.SETTLED, 5, "12345.67")), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("0.00");
        assertThat(result.exposedClaims()).isZero();
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("12345.67");
    }

    @Test
    void noClaimsMeansZeroExposure() {
        LiabilityExposureResponse result = calculator.calculate(List.of(), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("0.00");
        assertThat(result.totalLiabilityExposure().scale()).isEqualTo(2);
        assertThat(result.exposedClaims()).isZero();
        assertThat(result.excludedSettledAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void listsEveryStatusInLifecycleOrderWithZerosWhereThereAreNoClaims() {
        LiabilityExposureResponse result = calculator.calculate(List.of(total(ClaimStatus.APPROVED, 1, "10.00")), NOW);

        assertThat(result.byStatus()).extracting(StatusExposure::status).containsExactly(ClaimStatus.values());
        assertThat(result.byStatus()).filteredOn(entry -> entry.status() != ClaimStatus.APPROVED)
                .allSatisfy(entry -> {
                    assertThat(entry.claimCount()).isZero();
                    assertThat(entry.amount()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void flagsWhichStatusesTheFormulaIncludes() {
        LiabilityExposureResponse result = calculator.calculate(List.of(), NOW);

        assertThat(result.byStatus()).filteredOn(StatusExposure::includedInExposure)
                .extracting(StatusExposure::status)
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED,
                        ClaimStatus.APPROVED, ClaimStatus.REJECTED);
        assertThat(result.byStatus()).filteredOn(entry -> !entry.includedInExposure())
                .extracting(StatusExposure::status).containsExactly(ClaimStatus.SETTLED);
    }

    @Test
    void mergesDuplicateStatusRowsAndToleratesNullAmounts() {
        LiabilityExposureResponse result = calculator.calculate(List.of(
                total(ClaimStatus.SUBMITTED, 1, "100.00"),
                total(ClaimStatus.SUBMITTED, 2, "50.25"),
                new RemoteStatusTotal(ClaimStatus.APPROVED, 1, null)), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualByComparingTo("150.25");
        assertThat(result.exposedClaims()).isEqualTo(4);
        assertThat(result.byStatus().get(0)).isEqualTo(new StatusExposure(ClaimStatus.SUBMITTED, 3, new BigDecimal("150.25"), true));
    }

    @Test
    void doesNotLoseCentsToFloatingPointRounding() {
        LiabilityExposureResponse result = calculator.calculate(List.of(
                total(ClaimStatus.SUBMITTED, 1, "0.10"),
                total(ClaimStatus.UNDER_REVIEW, 1, "0.20")), NOW);

        assertThat(result.totalLiabilityExposure()).isEqualTo(new BigDecimal("0.30"));
    }

    private static RemoteStatusTotal total(ClaimStatus status, long count, String amount) {
        return new RemoteStatusTotal(status, count, new BigDecimal(amount));
    }
}
