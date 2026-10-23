package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.ClaimType;
import com.chubb.claims.assessment.client.Market;
import com.chubb.claims.assessment.client.RemoteStaffStatusTotal;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.client.RemoteTrend;
import com.chubb.claims.assessment.client.RemoteTrendPeriod;
import com.chubb.claims.assessment.client.RemoteTypeTotal;
import com.chubb.claims.assessment.client.StatisticsScope;
import com.chubb.claims.assessment.dto.ClaimTypeSummary;
import com.chubb.claims.assessment.dto.ClaimsOverTimeResponse;
import com.chubb.claims.assessment.dto.ClaimsPeriod;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketSummary;
import com.chubb.claims.assessment.dto.StaffWorkload;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.exception.ClaimServiceProtocolException;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dashboard metrics. The numbers come from claim-service's aggregates, read live on every call, so they can never
 * disagree with the claims themselves. Every dashboard can be limited to one market, one claim type and a range of
 * submission dates; money is reported in the configured reporting currency because claims are in their own market's currency.
 */
@Service
public class DashboardService {

    private final ClaimServiceClient claimServiceClient;
    private final ClaimAssessmentRepository assessmentRepository;
    private final LiabilityExposureCalculator liabilityExposureCalculator;
    private final ExchangeRates exchangeRates;
    private final Clock clock;

    public DashboardService(ClaimServiceClient claimServiceClient,
                            ClaimAssessmentRepository assessmentRepository,
                            LiabilityExposureCalculator liabilityExposureCalculator,
                            ExchangeRates exchangeRates,
                            Clock clock) {
        this.claimServiceClient = claimServiceClient;
        this.assessmentRepository = assessmentRepository;
        this.liabilityExposureCalculator = liabilityExposureCalculator;
        this.exchangeRates = exchangeRates;
        this.clock = clock;
    }

    /** @param scope the claims to look at; its market limits the headline figures, never the per-market and per-type breakdowns */
    public StatusSummaryResponse statusSummary(StatisticsScope scope) {
        Market market = scope.market();
        RemoteStatistics statistics = claimServiceClient.getStatistics(scope);
        List<RemoteStatusTotal> all = statistics.byStatus();
        Map<ClaimStatus, Long> counts = countsByStatus(inScope(all, market));

        return new StatusSummaryResponse(
                counts.values().stream().mapToLong(Long::longValue).sum(),
                pending(counts),
                counts.get(ClaimStatus.APPROVED),
                counts.get(ClaimStatus.REJECTED),
                counts.get(ClaimStatus.SETTLED),
                counts,
                market == null ? null : market.name(),
                scope.claimType(),
                scope.fromDate(),
                scope.toDate(),
                marketSummaries(all),
                claimTypeSummaries(statistics.byType(), market),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public StaffWorkloadResponse staffWorkload(StatisticsScope scope) {
        Market market = scope.market();
        RemoteStatistics statistics = claimServiceClient.getStatistics(scope);

        Map<Long, String> staffNames = new HashMap<>();
        assessmentRepository.findStaffNames()
                .forEach(staffMember -> staffNames.put(staffMember.getStaffId(), staffMember.getStaffName()));

        Map<Long, List<RemoteStaffStatusTotal>> byStaff = new LinkedHashMap<>();
        for (RemoteStaffStatusTotal total : statistics.byStaff()) {
            if (market == null || market.name().equals(total.market())) {
                byStaff.computeIfAbsent(total.staffId(), id -> new ArrayList<>()).add(total);
            }
        }

        List<StaffWorkload> staff = byStaff.entrySet().stream()
                .map(entry -> workloadOf(entry.getKey(), staffNames.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(StaffWorkload::openClaims).reversed()
                        .thenComparing(StaffWorkload::staffId))
                .toList();

        long unassigned = countsByStatus(inScope(statistics.byStatus(), market)).get(ClaimStatus.SUBMITTED);
        return new StaffWorkloadResponse(exchangeRates.reportingCurrency(), market == null ? null : market.name(),
                scope.claimType(), scope.fromDate(), scope.toDate(), unassigned, staff, clock.instant());
    }

    public LiabilityExposureResponse liabilityExposure(StatisticsScope scope) {
        return liabilityExposureCalculator.calculate(scope, claimServiceClient.getStatistics(scope), clock.instant());
    }

    /**
     * How many claims were submitted in each week or month, and where they stand now. Claim-service chooses the size of the
     * period from the window and fills the quiet ones; this adds the pending and decided totals the chart is drawn from, so the
     * rule for "pending" stays in one place.
     */
    public ClaimsOverTimeResponse claimsOverTime(StatisticsScope scope) {
        RemoteTrend trend = claimServiceClient.getTrend(scope);
        List<ClaimsPeriod> periods = trend.periods().stream().map(DashboardService::periodOf).toList();
        return new ClaimsOverTimeResponse(granularityOf(trend.granularity()), scope.market() == null ? null : scope.market().name(),
                scope.claimType(), scope.fromDate(), scope.toDate(), periods, clock.instant());
    }

    private static ClaimsPeriod periodOf(RemoteTrendPeriod period) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, period.claimsByStatus().getOrDefault(status, 0L));
        }
        long pending = pending(counts);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new ClaimsPeriod(period.start(), total, pending, total - pending, counts);
    }

    private static ClaimsOverTimeResponse.Granularity granularityOf(String granularity) {
        try {
            return ClaimsOverTimeResponse.Granularity.valueOf(granularity);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new ClaimServiceProtocolException("Claim service answered with an unknown trend granularity: " + granularity);
        }
    }

    private StaffWorkload workloadOf(Long staffId, String staffName, List<RemoteStaffStatusTotal> totals) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        BigDecimal openExposure = BigDecimal.ZERO.setScale(2);
        for (RemoteStaffStatusTotal total : totals) {
            counts.merge(total.status(), total.claimCount(), Long::sum);
            if (total.status().isOpenForStaff() && total.totalAmount() != null) {
                openExposure = openExposure.add(exchangeRates.toReportingCurrency(total.currency(), total.totalAmount()));
            }
        }

        long underReview = counts.getOrDefault(ClaimStatus.UNDER_REVIEW, 0L);
        long awaitingInformation = counts.getOrDefault(ClaimStatus.MORE_INFO_REQUIRED, 0L);
        return new StaffWorkload(
                staffId,
                staffName,
                underReview + awaitingInformation,
                underReview,
                awaitingInformation,
                counts.getOrDefault(ClaimStatus.APPROVED, 0L),
                counts.getOrDefault(ClaimStatus.REJECTED, 0L),
                counts.getOrDefault(ClaimStatus.SETTLED, 0L),
                counts.values().stream().mapToLong(Long::longValue).sum(),
                openExposure);
    }

    /** One row per market that has claims, in country-code order; never narrowed by the market filter. */
    private static List<MarketSummary> marketSummaries(List<RemoteStatusTotal> all) {
        Map<String, List<RemoteStatusTotal>> byMarket = new TreeMap<>();
        all.forEach(total -> byMarket.computeIfAbsent(total.market(), code -> new ArrayList<>()).add(total));

        List<MarketSummary> summaries = new ArrayList<>();
        byMarket.forEach((code, totals) -> {
            Map<ClaimStatus, Long> counts = countsByStatus(totals);
            summaries.add(new MarketSummary(code,
                    counts.values().stream().mapToLong(Long::longValue).sum(),
                    pending(counts),
                    counts.get(ClaimStatus.APPROVED),
                    counts.get(ClaimStatus.REJECTED),
                    counts.get(ClaimStatus.SETTLED)));
        });
        return List.copyOf(summaries);
    }

    /** One row per claim type that has claims, in the order of the claim types; follows the market, never the type filter. */
    private static List<ClaimTypeSummary> claimTypeSummaries(List<RemoteTypeTotal> typeTotals, Market market) {
        Map<ClaimType, List<RemoteTypeTotal>> byType = new EnumMap<>(ClaimType.class);
        typeTotals.stream()
                .filter(total -> market == null || market.name().equals(total.market()))
                .forEach(total -> byType.computeIfAbsent(total.claimType(), type -> new ArrayList<>()).add(total));

        List<ClaimTypeSummary> summaries = new ArrayList<>();
        byType.forEach((type, totals) -> {
            Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
            for (ClaimStatus status : ClaimStatus.values()) {
                counts.put(status, 0L);
            }
            totals.forEach(total -> counts.merge(total.status(), total.claimCount(), Long::sum));
            summaries.add(new ClaimTypeSummary(type,
                    counts.values().stream().mapToLong(Long::longValue).sum(),
                    pending(counts),
                    counts.get(ClaimStatus.APPROVED),
                    counts.get(ClaimStatus.REJECTED),
                    counts.get(ClaimStatus.SETTLED)));
        });
        return List.copyOf(summaries);
    }

    private static long pending(Map<ClaimStatus, Long> counts) {
        return counts.entrySet().stream().filter(entry -> entry.getKey().isPending()).mapToLong(Map.Entry::getValue).sum();
    }

    private static List<RemoteStatusTotal> inScope(List<RemoteStatusTotal> totals, Market market) {
        return market == null ? totals : totals.stream().filter(total -> market.name().equals(total.market())).toList();
    }

    /** Every status is present, zero when claim-service reported none. */
    private static Map<ClaimStatus, Long> countsByStatus(List<RemoteStatusTotal> totals) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        totals.forEach(total -> counts.merge(total.status(), total.claimCount(), Long::sum));
        return counts;
    }
}
