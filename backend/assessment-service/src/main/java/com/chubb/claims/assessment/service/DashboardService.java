package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteOfficerStatusTotal;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.OfficerWorkload;
import com.chubb.claims.assessment.dto.OfficerWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard metrics. The numbers come from claim-service's aggregates, read live on every call, so they can never
 * disagree with the claims themselves.
 */
@Service
public class DashboardService {

    private final ClaimServiceClient claimServiceClient;
    private final ClaimAssessmentRepository assessmentRepository;
    private final LiabilityExposureCalculator liabilityExposureCalculator;
    private final Clock clock;

    public DashboardService(ClaimServiceClient claimServiceClient,
                            ClaimAssessmentRepository assessmentRepository,
                            LiabilityExposureCalculator liabilityExposureCalculator,
                            Clock clock) {
        this.claimServiceClient = claimServiceClient;
        this.assessmentRepository = assessmentRepository;
        this.liabilityExposureCalculator = liabilityExposureCalculator;
        this.clock = clock;
    }

    public StatusSummaryResponse statusSummary() {
        Map<ClaimStatus, Long> counts = countsByStatus(claimServiceClient.getStatistics().byStatus());

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long pending = counts.entrySet().stream().filter(entry -> entry.getKey().isPending())
                .mapToLong(Map.Entry::getValue).sum();

        return new StatusSummaryResponse(
                total,
                pending,
                counts.get(ClaimStatus.APPROVED),
                counts.get(ClaimStatus.REJECTED),
                counts.get(ClaimStatus.SETTLED),
                counts,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public OfficerWorkloadResponse officerWorkload() {
        RemoteStatistics statistics = claimServiceClient.getStatistics();

        Map<Long, String> officerNames = new HashMap<>();
        assessmentRepository.findOfficerNames()
                .forEach(officer -> officerNames.put(officer.getOfficerId(), officer.getOfficerName()));

        Map<Long, List<RemoteOfficerStatusTotal>> byOfficer = new LinkedHashMap<>();
        for (RemoteOfficerStatusTotal total : statistics.byOfficer()) {
            byOfficer.computeIfAbsent(total.officerId(), id -> new java.util.ArrayList<>()).add(total);
        }

        List<OfficerWorkload> officers = byOfficer.entrySet().stream()
                .map(entry -> workloadOf(entry.getKey(), officerNames.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(OfficerWorkload::openClaims).reversed()
                        .thenComparing(OfficerWorkload::officerId))
                .toList();

        long unassigned = countsByStatus(statistics.byStatus()).get(ClaimStatus.SUBMITTED);
        return new OfficerWorkloadResponse(unassigned, officers, clock.instant());
    }

    public LiabilityExposureResponse liabilityExposure() {
        return liabilityExposureCalculator.calculate(claimServiceClient.getStatistics().byStatus(), clock.instant());
    }

    private static OfficerWorkload workloadOf(Long officerId, String officerName, List<RemoteOfficerStatusTotal> totals) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        BigDecimal openExposure = BigDecimal.ZERO.setScale(2);
        for (RemoteOfficerStatusTotal total : totals) {
            counts.merge(total.status(), total.claimCount(), Long::sum);
            if (total.status().isOpenForOfficer() && total.totalAmount() != null) {
                openExposure = openExposure.add(total.totalAmount());
            }
        }

        long underReview = counts.getOrDefault(ClaimStatus.UNDER_REVIEW, 0L);
        long awaitingInformation = counts.getOrDefault(ClaimStatus.MORE_INFO_REQUIRED, 0L);
        return new OfficerWorkload(
                officerId,
                officerName,
                underReview + awaitingInformation,
                underReview,
                awaitingInformation,
                counts.getOrDefault(ClaimStatus.APPROVED, 0L),
                counts.getOrDefault(ClaimStatus.REJECTED, 0L),
                counts.getOrDefault(ClaimStatus.SETTLED, 0L),
                counts.values().stream().mapToLong(Long::longValue).sum(),
                openExposure);
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
