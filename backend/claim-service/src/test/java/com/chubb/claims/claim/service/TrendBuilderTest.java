package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ClaimTrendResponse;
import com.chubb.claims.claim.dto.ClaimTrendResponse.Granularity;
import com.chubb.claims.claim.dto.TrendPeriod;
import com.chubb.claims.claim.dto.TrendRow;
import com.chubb.claims.claim.entity.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendBuilderTest {

    @Test
    void noClaimsMeansNoPeriods() {
        ClaimTrendResponse trend = TrendBuilder.build(List.of(), null, null);

        assertThat(trend.periods()).isEmpty();
        assertThat(trend.granularity()).isEqualTo(Granularity.WEEK);
    }

    @Test
    void noClaimsInABoundedWindowStillSaysHowThatWindowWouldBeDrawn() {
        assertThat(TrendBuilder.build(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).granularity())
                .isEqualTo(Granularity.WEEK);
        assertThat(TrendBuilder.build(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).granularity())
                .isEqualTo(Granularity.MONTH);
    }

    @Test
    void weeksStartOnMondayAndHoldTheClaimsSubmittedInThem() {
        // Wednesday 5 Aug, Sunday 9 Aug (same week), Monday 10 Aug (next week), all UTC.
        List<TrendRow> rows = List.of(
                row("2026-08-05T10:00:00Z", ClaimStatus.SUBMITTED),
                row("2026-08-09T23:59:59Z", ClaimStatus.APPROVED),
                row("2026-08-10T00:00:00Z", ClaimStatus.UNDER_REVIEW));

        ClaimTrendResponse trend = TrendBuilder.build(rows, null, null);

        assertThat(trend.granularity()).isEqualTo(Granularity.WEEK);
        assertThat(trend.periods()).extracting(TrendPeriod::start).containsExactly(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));
        assertThat(trend.periods()).allSatisfy(period -> assertThat(period.start().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
        assertThat(trend.periods().get(0).claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 1L).containsEntry(ClaimStatus.APPROVED, 1L);
        assertThat(trend.periods().get(1).claimsByStatus()).containsEntry(ClaimStatus.UNDER_REVIEW, 1L);
    }

    @Test
    void everyStatusIsPresentInEveryPeriodAndNothingIsLostOrCountedTwice() {
        List<TrendRow> rows = List.of(row("2026-08-05T10:00:00Z", ClaimStatus.REJECTED), row("2026-08-06T10:00:00Z", ClaimStatus.REJECTED));

        ClaimTrendResponse trend = TrendBuilder.build(rows, null, null);

        assertThat(trend.periods()).singleElement().satisfies(period -> {
            assertThat(period.claimsByStatus()).containsOnlyKeys(ClaimStatus.values());
            assertThat(period.claimsByStatus().get(ClaimStatus.REJECTED)).isEqualTo(2L);
            assertThat(period.claimsByStatus().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(2L);
        });
    }

    @Test
    void quietPeriodsAppearAsZeroNotAsAGap() {
        List<TrendRow> rows = List.of(row("2026-08-03T09:00:00Z", ClaimStatus.SUBMITTED), row("2026-08-24T09:00:00Z", ClaimStatus.SUBMITTED));

        ClaimTrendResponse trend = TrendBuilder.build(rows, null, null);

        assertThat(trend.periods()).extracting(TrendPeriod::start).containsExactly(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24));
        assertThat(trend.periods().get(1).claimsByStatus().values()).containsOnly(0L);
        assertThat(trend.periods().get(2).claimsByStatus().values()).containsOnly(0L);
    }

    @Test
    void aRequestedWindowIsFilledToItsEdgesEvenWhereNothingWasSubmitted() {
        List<TrendRow> rows = List.of(row("2026-08-12T09:00:00Z", ClaimStatus.SUBMITTED));

        ClaimTrendResponse trend = TrendBuilder.build(rows, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 30));

        assertThat(trend.periods()).extracting(TrendPeriod::start).containsExactly(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24));
    }

    @Test
    void aWindowOfUpTo183DaysIsDrawnInWeeksAndLongerOnesInMonths() {
        List<TrendRow> rows = List.of(row("2026-01-01T00:00:00Z", ClaimStatus.SUBMITTED), row("2026-07-02T00:00:00Z", ClaimStatus.SUBMITTED));
        // 1 Jan to 2 Jul is 183 days: still weeks. One day more is months.
        assertThat(TrendBuilder.build(rows, null, null).granularity()).isEqualTo(Granularity.WEEK);

        List<TrendRow> longer = List.of(row("2026-01-01T00:00:00Z", ClaimStatus.SUBMITTED), row("2026-07-03T00:00:00Z", ClaimStatus.SUBMITTED));
        assertThat(TrendBuilder.build(longer, null, null).granularity()).isEqualTo(Granularity.MONTH);
    }

    @Test
    void monthsStartOnTheFirstAndEveryMonthInBetweenAppears() {
        List<TrendRow> rows = List.of(
                row("2026-01-31T23:00:00Z", ClaimStatus.SUBMITTED),
                row("2026-04-15T12:00:00Z", ClaimStatus.APPROVED),
                row("2026-10-22T05:00:00Z", ClaimStatus.SETTLED));

        ClaimTrendResponse trend = TrendBuilder.build(rows, null, null);

        assertThat(trend.granularity()).isEqualTo(Granularity.MONTH);
        assertThat(trend.periods()).hasSize(10);
        assertThat(trend.periods()).extracting(TrendPeriod::start).allSatisfy(start -> assertThat(start.getDayOfMonth()).isEqualTo(1));
        assertThat(trend.periods().get(0).start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(trend.periods().get(9).start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(trend.periods().get(0).claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 1L);
        assertThat(trend.periods().get(3).claimsByStatus()).containsEntry(ClaimStatus.APPROVED, 1L);
        assertThat(trend.periods().get(9).claimsByStatus()).containsEntry(ClaimStatus.SETTLED, 1L);
    }

    @Test
    void daysAreCutInUtcWhateverTheServersZoneIs() {
        // 18:30 UTC on Sunday is already Monday in Singapore and Sydney; the week must still be the UTC one.
        ClaimTrendResponse trend = TrendBuilder.build(List.of(row("2026-08-09T18:30:00Z", ClaimStatus.SUBMITTED)), null, null);

        assertThat(trend.periods()).extracting(TrendPeriod::start).containsExactly(LocalDate.of(2026, 8, 3));
    }

    @Test
    void anAbsurdWindowFallsBackToTheSpanOfTheClaimsSoTheAnswerStaysSmall() {
        List<TrendRow> rows = List.of(row("2026-08-05T10:00:00Z", ClaimStatus.SUBMITTED), row("2026-09-05T10:00:00Z", ClaimStatus.SUBMITTED));

        ClaimTrendResponse trend = TrendBuilder.build(rows, LocalDate.of(1900, 1, 1), LocalDate.of(9000, 1, 1));

        assertThat(trend.granularity()).isEqualTo(Granularity.WEEK);
        assertThat(trend.periods()).hasSizeLessThan(10);
        assertThat(trend.periods().get(0).start()).isEqualTo(LocalDate.of(2026, 8, 3));
    }

    private static TrendRow row(String createdAt, ClaimStatus status) {
        return new TrendRow(Instant.parse(createdAt), status);
    }
}
