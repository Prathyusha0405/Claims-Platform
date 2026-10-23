package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ClaimTrendResponse;
import com.chubb.claims.claim.dto.ClaimTrendResponse.Granularity;
import com.chubb.claims.claim.dto.TrendPeriod;
import com.chubb.claims.claim.dto.TrendRow;
import com.chubb.claims.claim.entity.ClaimStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns claims into periods: weeks (Monday to Sunday) for a window of up to {@value #WEEK_LIMIT_DAYS} days, months beyond
 * that, all in UTC like every other date in the service. A pure function of the rows and the requested dates, so it is easy to
 * test and has no say in which claims are counted.
 * <p>
 * The window is the requested dates where given and the first and last claim otherwise. Every period in it appears, with zeros
 * where nothing was submitted, so a quiet week shows as a gap in the chart and not as a missing column. A window longer than
 * {@value #MAX_WINDOW_DAYS} days falls back to the span of the claims themselves, which keeps the answer small whatever dates
 * are asked for.
 */
final class TrendBuilder {

    static final int WEEK_LIMIT_DAYS = 183;
    static final int MAX_WINDOW_DAYS = 3660;

    private TrendBuilder() {
    }

    static ClaimTrendResponse build(List<TrendRow> rows, LocalDate fromDate, LocalDate toDate) {
        if (rows.isEmpty()) {
            boolean bounded = fromDate != null && toDate != null;
            return new ClaimTrendResponse(bounded ? granularityFor(fromDate, toDate) : Granularity.WEEK, List.of());
        }

        LocalDate first = rows.stream().map(row -> day(row)).min(LocalDate::compareTo).orElseThrow();
        LocalDate last = rows.stream().map(row -> day(row)).max(LocalDate::compareTo).orElseThrow();
        LocalDate windowStart = fromDate != null ? fromDate : first;
        LocalDate windowEnd = toDate != null ? toDate : last;
        if (ChronoUnit.DAYS.between(windowStart, windowEnd) > MAX_WINDOW_DAYS) {
            windowStart = first;
            windowEnd = last;
        }

        Granularity granularity = granularityFor(windowStart, windowEnd);
        Map<LocalDate, Map<ClaimStatus, Long>> periods = new TreeMap<>();
        for (LocalDate start = periodStart(granularity, windowStart); !start.isAfter(windowEnd); start = next(granularity, start)) {
            periods.put(start, zeroCounts());
        }
        for (TrendRow row : rows) {
            periods.get(periodStart(granularity, day(row))).merge(row.status(), 1L, Long::sum);
        }

        List<TrendPeriod> result = new ArrayList<>();
        periods.forEach((start, counts) -> result.add(new TrendPeriod(start, counts)));
        return new ClaimTrendResponse(granularity, List.copyOf(result));
    }

    private static Granularity granularityFor(LocalDate windowStart, LocalDate windowEnd) {
        long days = ChronoUnit.DAYS.between(windowStart, windowEnd) + 1;
        return days <= WEEK_LIMIT_DAYS ? Granularity.WEEK : Granularity.MONTH;
    }

    private static LocalDate day(TrendRow row) {
        return row.createdAt().atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate periodStart(Granularity granularity, LocalDate date) {
        return granularity == Granularity.WEEK
                ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : date.withDayOfMonth(1);
    }

    private static LocalDate next(Granularity granularity, LocalDate periodStart) {
        return granularity == Granularity.WEEK ? periodStart.plusWeeks(1) : periodStart.plusMonths(1);
    }

    /** Every status is present, zero until a claim says otherwise. */
    private static Map<ClaimStatus, Long> zeroCounts() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }
}
