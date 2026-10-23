package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.StatisticsCriteria;
import com.chubb.claims.claim.dto.TrendCriteria;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimScopeTest {

    @Test
    void noCriteriaMeansEveryClaim() {
        ClaimScope scope = ClaimScope.of(new StatisticsCriteria(null, null, null));

        assertThat(scope).isEqualTo(ClaimScope.all());
        assertThat(scope.from()).isEqualTo(Instant.EPOCH);
        assertThat(scope.toExclusive()).isAfter(Instant.parse("3000-01-01T00:00:00Z"));
        assertThat(scope.anyMarket()).isTrue();
        assertThat(scope.anyType()).isTrue();
    }

    @Test
    void datesAreWholeUtcDaysAndBothEndsAreInclusive() {
        ClaimScope scope = ClaimScope.of(new StatisticsCriteria(null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertThat(scope.from()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(scope.toExclusive()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void oneDayIsAValidWindow() {
        ClaimScope scope = ClaimScope.of(new StatisticsCriteria(null, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5)));

        assertThat(scope.from()).isEqualTo(Instant.parse("2026-08-05T00:00:00Z"));
        assertThat(scope.toExclusive()).isEqualTo(Instant.parse("2026-08-06T00:00:00Z"));
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> ClaimScope.of(new StatisticsCriteria(null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("fromDate must not be after toDate");
    }

    @Test
    void theTrendCriteriaAlsoCarriesTheMarket() {
        ClaimScope scope = ClaimScope.of(new TrendCriteria(Market.JP, ClaimType.HOME, null, null));

        assertThat(scope.market()).isEqualTo(Market.JP);
        assertThat(scope.claimType()).isEqualTo(ClaimType.HOME);
        assertThat(scope.anyMarket()).isFalse();
        assertThat(scope.anyType()).isFalse();
        assertThat(scope.marketOrPlaceholder()).isEqualTo(Market.JP);
        assertThat(scope.typeOrPlaceholder()).isEqualTo(ClaimType.HOME);
    }

    @Test
    void anAbsentMarketOrTypeIsAFlagPlusAPlaceholderNeverANullParameter() {
        ClaimScope scope = ClaimScope.all();

        assertThat(scope.marketOrPlaceholder()).isNotNull();
        assertThat(scope.typeOrPlaceholder()).isNotNull();
    }

    @Test
    void withAnyTypeKeepsTheWindowAndTheMarketButDropsTheType() {
        ClaimScope scope = ClaimScope.of(new TrendCriteria(Market.SG, ClaimType.AUTO, LocalDate.of(2026, 8, 1), null)).withAnyType();

        assertThat(scope.anyType()).isTrue();
        assertThat(scope.market()).isEqualTo(Market.SG);
        assertThat(scope.from()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }
}
