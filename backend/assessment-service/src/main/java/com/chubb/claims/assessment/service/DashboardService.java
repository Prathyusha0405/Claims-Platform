package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.Market;
import com.chubb.claims.assessment.client.RemoteStaffStatusTotal;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketSummary;
import com.chubb.claims.assessment.dto.StaffWorkload;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.exception.InvalidRequestException;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dashboard metrics. The numbers come from claim-service's aggregates, read live on every call, so they can never
 * disagree with the claims themselves. Every dashboard can be limited to one market; money is reported in the
 * configured reporting currency because claims are in their own market's currency.
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

    /** @param marketCode an ISO country code to limit the headline figures to, or null for every market */
    public StatusSummaryResponse statusSummary(String marketCode) {
        Market market = parseMarket(marketCode);
        List<RemoteStatusTotal> all = claimServiceClient.getStatistics().byStatus();
        Map<ClaimStatus, Long> counts = countsByStatus(inScope(all, market));

        return new StatusSummaryResponse(
                counts.values().stream().mapToLong(Long::longValue).sum(),
                pending(counts),
                counts.get(ClaimStatus.APPROVED),
                counts.get(ClaimStatus.REJECTED),
                counts.get(ClaimStatus.SETTLED),
                counts,
                market == null ? null : market.name(),
                marketSummaries(all),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public StaffWorkloadResponse staffWorkload(String marketCode) {
        Market market = parseMarket(marketCode);
        RemoteStatistics statistics = claimServiceClient.getStatistics();

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
                unassigned, staff, clock.instant());
    }

    public LiabilityExposureResponse liabilityExposure(String marketCode) {
        Market market = parseMarket(marketCode);
        return liabilityExposureCalculator.calculate(market == null ? null : market.name(),
                claimServiceClient.getStatistics().byStatus(), clock.instant());
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

    /** Null or blank means "every market"; anything that is not one of the six is a client error, as on claim search. */
    private static Market parseMarket(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Market.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidRequestException("Unknown market '" + code.trim() + "'; allowed values: "
                    + Arrays.toString(Market.values()));
        }
    }
}
