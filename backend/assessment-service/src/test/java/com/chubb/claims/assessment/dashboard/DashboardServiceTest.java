package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteOfficerStatusTotal;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.OfficerWorkload;
import com.chubb.claims.assessment.dto.OfficerWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.service.DashboardService;
import com.chubb.claims.assessment.service.LiabilityExposureCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Dashboard metrics over claim-service's aggregates. The sample data: 13 claims (6 pending, 4 approved, 1 rejected,
 * 2 settled), three officers, 3 claims still in the work queue.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimServiceClient claimServiceClient;

    @Mock
    private ClaimAssessmentRepository assessmentRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(claimServiceClient, assessmentRepository, new LiabilityExposureCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- status summary ---------------------------------------------------------------------------------------

    @Test
    void statusSummaryCountsTotalPendingApprovedRejectedAndSettled() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        StatusSummaryResponse summary = dashboardService.statusSummary();

        assertThat(summary.totalClaims()).isEqualTo(13);
        assertThat(summary.pendingClaims()).isEqualTo(6);       // 3 submitted + 2 under review + 1 awaiting info
        assertThat(summary.approvedClaims()).isEqualTo(4);
        assertThat(summary.rejectedClaims()).isEqualTo(1);
        assertThat(summary.settledClaims()).isEqualTo(2);
        assertThat(summary.totalClaims()).isEqualTo(
                summary.pendingClaims() + summary.approvedClaims() + summary.rejectedClaims() + summary.settledClaims());
        assertThat(summary.claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 3L).containsEntry(ClaimStatus.MORE_INFO_REQUIRED, 1L);
        assertThat(summary.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void statusSummaryListsEveryStatusEvenWhenThereAreNoClaims() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(List.of(), List.of()));

        StatusSummaryResponse summary = dashboardService.statusSummary();

        assertThat(summary.totalClaims()).isZero();
        assertThat(summary.pendingClaims()).isZero();
        assertThat(summary.claimsByStatus()).containsOnlyKeys(ClaimStatus.values());
        assertThat(summary.claimsByStatus().values()).containsOnly(0L);
    }

    // ---- officer workload -------------------------------------------------------------------------------------

    @Test
    void officerWorkloadBreaksDownEachOfficersClaimsAndSortsBusiestFirst() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());
        when(assessmentRepository.findOfficerNames()).thenReturn(List.of(
                officerName(7L, "Alice Officer"), officerName(8L, "Bob Officer")));

        OfficerWorkloadResponse workload = dashboardService.officerWorkload();

        assertThat(workload.unassignedClaims()).isEqualTo(3);
        assertThat(workload.generatedAt()).isEqualTo(NOW);
        assertThat(workload.officers()).extracting(OfficerWorkload::officerId).containsExactly(7L, 8L, 9L);

        OfficerWorkload alice = workload.officers().get(0);
        assertThat(alice.officerName()).isEqualTo("Alice Officer");
        assertThat(alice.openClaims()).isEqualTo(2);
        assertThat(alice.underReview()).isEqualTo(2);
        assertThat(alice.awaitingInformation()).isZero();
        assertThat(alice.approved()).isEqualTo(3);
        assertThat(alice.rejected()).isZero();
        assertThat(alice.totalAssigned()).isEqualTo(5);
        assertThat(alice.openExposure()).isEqualByComparingTo("2000.00");

        OfficerWorkload bob = workload.officers().get(1);
        assertThat(bob.openClaims()).isEqualTo(1);
        assertThat(bob.awaitingInformation()).isEqualTo(1);
        assertThat(bob.approved()).isEqualTo(1);
        assertThat(bob.rejected()).isEqualTo(1);
        assertThat(bob.totalAssigned()).isEqualTo(3);
        assertThat(bob.openExposure()).isEqualByComparingTo("500.50");
    }

    @Test
    void anOfficerWithOnlyFinishedClaimsHasNoOpenWorkAndNoExposure() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());
        when(assessmentRepository.findOfficerNames()).thenReturn(List.of());

        OfficerWorkload carol = dashboardService.officerWorkload().officers().get(2);

        assertThat(carol.officerId()).isEqualTo(9L);
        assertThat(carol.officerName()).isNull();
        assertThat(carol.openClaims()).isZero();
        assertThat(carol.settled()).isEqualTo(2);
        assertThat(carol.totalAssigned()).isEqualTo(2);
        assertThat(carol.openExposure()).isEqualByComparingTo("0.00");
    }

    @Test
    void equallyBusyOfficersAreOrderedById() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(
                List.of(),
                List.of(officerTotal(12L, ClaimStatus.UNDER_REVIEW, 1, "10.00"), officerTotal(5L, ClaimStatus.UNDER_REVIEW, 1, "10.00"))));
        when(assessmentRepository.findOfficerNames()).thenReturn(List.of());

        assertThat(dashboardService.officerWorkload().officers()).extracting(OfficerWorkload::officerId).containsExactly(5L, 12L);
    }

    @Test
    void workloadWithNoAssignedClaimsListsNoOfficers() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(
                List.of(new RemoteStatusTotal(ClaimStatus.SUBMITTED, 4, new BigDecimal("400.00"))), List.of()));
        when(assessmentRepository.findOfficerNames()).thenReturn(List.of());

        OfficerWorkloadResponse workload = dashboardService.officerWorkload();

        assertThat(workload.officers()).isEmpty();
        assertThat(workload.unassignedClaims()).isEqualTo(4);
    }

    // ---- liability exposure -----------------------------------------------------------------------------------

    @Test
    void liabilityExposureAppliesTheFormulaToClaimServicesTotals() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        LiabilityExposureResponse exposure = dashboardService.liabilityExposure();

        assertThat(exposure.totalLiabilityExposure()).isEqualByComparingTo("6900.50");
        assertThat(exposure.exposedClaims()).isEqualTo(11);
        assertThat(exposure.excludedSettledAmount()).isEqualByComparingTo("900.00");
        assertThat(exposure.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void anOutageOfClaimServiceFailsTheDashboardInsteadOfShowingStaleNumbers() {
        when(claimServiceClient.getStatistics()).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        assertThatThrownBy(() -> dashboardService.statusSummary()).isInstanceOf(ClaimServiceUnavailableException.class);
        assertThatThrownBy(() -> dashboardService.liabilityExposure()).isInstanceOf(ClaimServiceUnavailableException.class);
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    private static RemoteStatistics sampleStatistics() {
        return new RemoteStatistics(
                List.of(
                        statusTotal(ClaimStatus.SUBMITTED, 3, "300.00"),
                        statusTotal(ClaimStatus.UNDER_REVIEW, 2, "2000.00"),
                        statusTotal(ClaimStatus.MORE_INFO_REQUIRED, 1, "500.50"),
                        statusTotal(ClaimStatus.APPROVED, 4, "4000.00"),
                        statusTotal(ClaimStatus.REJECTED, 1, "100.00"),
                        statusTotal(ClaimStatus.SETTLED, 2, "900.00")),
                List.of(
                        officerTotal(7L, ClaimStatus.UNDER_REVIEW, 2, "2000.00"),
                        officerTotal(7L, ClaimStatus.APPROVED, 3, "3000.00"),
                        officerTotal(8L, ClaimStatus.MORE_INFO_REQUIRED, 1, "500.50"),
                        officerTotal(8L, ClaimStatus.APPROVED, 1, "1000.00"),
                        officerTotal(8L, ClaimStatus.REJECTED, 1, "100.00"),
                        officerTotal(9L, ClaimStatus.SETTLED, 2, "900.00")));
    }

    private static RemoteStatusTotal statusTotal(ClaimStatus status, long count, String amount) {
        return new RemoteStatusTotal(status, count, new BigDecimal(amount));
    }

    private static RemoteOfficerStatusTotal officerTotal(Long officerId, ClaimStatus status, long count, String amount) {
        return new RemoteOfficerStatusTotal(officerId, status, count, new BigDecimal(amount));
    }

    private static ClaimAssessmentRepository.OfficerName officerName(Long id, String name) {
        return new ClaimAssessmentRepository.OfficerName() {
            @Override
            public Long getOfficerId() {
                return id;
            }

            @Override
            public String getOfficerName() {
                return name;
            }
        };
    }
}
