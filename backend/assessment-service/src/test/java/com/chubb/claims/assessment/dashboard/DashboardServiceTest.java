package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimServiceClient;
import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteStaffStatusTotal;
import com.chubb.claims.assessment.client.RemoteStatistics;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.config.DashboardProperties;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketExposure;
import com.chubb.claims.assessment.dto.MarketSummary;
import com.chubb.claims.assessment.dto.StaffWorkload;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.exception.InvalidRequestException;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.service.DashboardService;
import com.chubb.claims.assessment.service.ExchangeRates;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Dashboard metrics over claim-service's aggregates. The sample data: 13 claims (6 pending, 4 approved, 1 rejected,
 * 2 settled) in two markets, 9 in Singapore (SGD) and 4 in Japan (JPY), three claims staff, 3 claims still in the work queue.
 * At the test rates (SGD 1.35 and JPY 150 per USD) every total converts to a round USD figure.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimServiceClient claimServiceClient;

    @Mock
    private ClaimAssessmentRepository assessmentRepository;

    private static final ExchangeRates RATES = new ExchangeRates(new DashboardProperties("USD",
            Map.of("SGD", new BigDecimal("1.35"), "JPY", new BigDecimal("150"), "AUD", new BigDecimal("1.50"))));

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(claimServiceClient, assessmentRepository,
                new LiabilityExposureCalculator(RATES), RATES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- status summary ---------------------------------------------------------------------------------------

    @Test
    void statusSummaryCountsTotalPendingApprovedRejectedAndSettled() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        StatusSummaryResponse summary = dashboardService.statusSummary(null);

        assertThat(summary.totalClaims()).isEqualTo(13);
        assertThat(summary.pendingClaims()).isEqualTo(6);       // 3 submitted + 2 under review + 1 awaiting info
        assertThat(summary.approvedClaims()).isEqualTo(4);
        assertThat(summary.rejectedClaims()).isEqualTo(1);
        assertThat(summary.settledClaims()).isEqualTo(2);
        assertThat(summary.totalClaims()).isEqualTo(
                summary.pendingClaims() + summary.approvedClaims() + summary.rejectedClaims() + summary.settledClaims());
        assertThat(summary.claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 3L).containsEntry(ClaimStatus.MORE_INFO_REQUIRED, 1L);
        assertThat(summary.market()).isNull();
        assertThat(summary.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void statusSummaryBreaksTheClaimsDownByMarket() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        StatusSummaryResponse summary = dashboardService.statusSummary(null);

        assertThat(summary.byMarket()).containsExactly(
                new MarketSummary("JP", 4, 2, 1, 1, 0),
                new MarketSummary("SG", 9, 4, 3, 0, 2));
    }

    @Test
    void statusSummaryCanBeLimitedToOneMarketButTheBreakdownStillShowsEveryMarket() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        StatusSummaryResponse summary = dashboardService.statusSummary("jp");   // case does not matter

        assertThat(summary.market()).isEqualTo("JP");
        assertThat(summary.totalClaims()).isEqualTo(4);
        assertThat(summary.pendingClaims()).isEqualTo(2);
        assertThat(summary.approvedClaims()).isEqualTo(1);
        assertThat(summary.rejectedClaims()).isEqualTo(1);
        assertThat(summary.settledClaims()).isZero();
        assertThat(summary.claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 1L).containsEntry(ClaimStatus.UNDER_REVIEW, 0L);
        assertThat(summary.byMarket()).extracting(MarketSummary::market).containsExactly("JP", "SG");
    }

    @Test
    void aBlankMarketMeansEveryMarketAndAKnownMarketWithoutClaimsGivesZeros() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        assertThat(dashboardService.statusSummary("  ").totalClaims()).isEqualTo(13);
        StatusSummaryResponse hongKong = dashboardService.statusSummary("HK");
        assertThat(hongKong.market()).isEqualTo("HK");
        assertThat(hongKong.totalClaims()).isZero();
        assertThat(hongKong.claimsByStatus()).containsOnlyKeys(ClaimStatus.values());
    }

    @Test
    void anUnknownMarketIsAClientErrorOnEveryDashboard() {
        assertThatThrownBy(() -> dashboardService.statusSummary("XX")).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("'XX'").hasMessageContaining("[AU, HK, JP, MY, NZ, SG]");
        assertThatThrownBy(() -> dashboardService.staffWorkload("Japan")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> dashboardService.liabilityExposure("USD")).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(claimServiceClient);
    }

    @Test
    void statusSummaryListsEveryStatusEvenWhenThereAreNoClaims() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(List.of(), List.of()));

        StatusSummaryResponse summary = dashboardService.statusSummary(null);

        assertThat(summary.totalClaims()).isZero();
        assertThat(summary.pendingClaims()).isZero();
        assertThat(summary.claimsByStatus()).containsOnlyKeys(ClaimStatus.values());
        assertThat(summary.claimsByStatus().values()).containsOnly(0L);
    }

    // ---- staff workload -------------------------------------------------------------------------------------

    @Test
    void staffWorkloadBreaksDownEachStaffMembersClaimsAndSortsBusiestFirst() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());
        when(assessmentRepository.findStaffNames()).thenReturn(List.of(
                staffName(7L, "Alice Staff"), staffName(8L, "Bob Staff")));

        StaffWorkloadResponse workload = dashboardService.staffWorkload(null);

        assertThat(workload.unassignedClaims()).isEqualTo(3);
        assertThat(workload.reportingCurrency()).isEqualTo("USD");
        assertThat(workload.market()).isNull();
        assertThat(workload.generatedAt()).isEqualTo(NOW);
        assertThat(workload.staff()).extracting(StaffWorkload::staffId).containsExactly(7L, 8L, 9L);

        StaffWorkload alice = workload.staff().get(0);
        assertThat(alice.staffName()).isEqualTo("Alice Staff");
        assertThat(alice.openClaims()).isEqualTo(2);
        assertThat(alice.underReview()).isEqualTo(2);
        assertThat(alice.awaitingInformation()).isZero();
        assertThat(alice.approved()).isEqualTo(3);
        assertThat(alice.rejected()).isZero();
        assertThat(alice.totalAssigned()).isEqualTo(5);
        assertThat(alice.openExposure()).isEqualByComparingTo("2000.00");

        StaffWorkload bob = workload.staff().get(1);
        assertThat(bob.openClaims()).isEqualTo(1);
        assertThat(bob.awaitingInformation()).isEqualTo(1);
        assertThat(bob.approved()).isEqualTo(1);
        assertThat(bob.rejected()).isEqualTo(1);
        assertThat(bob.totalAssigned()).isEqualTo(3);
        assertThat(bob.openExposure()).isEqualByComparingTo("500.50");
    }

    @Test
    void staffWorkloadCanBeLimitedToOneMarket() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());
        when(assessmentRepository.findStaffNames()).thenReturn(List.of(staffName(8L, "Bob Staff")));

        StaffWorkloadResponse workload = dashboardService.staffWorkload("JP");

        assertThat(workload.market()).isEqualTo("JP");
        assertThat(workload.unassignedClaims()).isEqualTo(1);                       // the one submitted Japanese claim
        assertThat(workload.staff()).extracting(StaffWorkload::staffId).containsExactly(8L);
        StaffWorkload bob = workload.staff().get(0);
        assertThat(bob.totalAssigned()).isEqualTo(3);
        assertThat(bob.openExposure()).isEqualByComparingTo("500.50");           // 75,075 JPY at 150 per USD
    }

    @Test
    void aClaimsStaffMembersOpenExposureAddsUpTheirMarketsInTheReportingCurrency() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(List.of(), List.of(
                new RemoteStaffStatusTotal(7L, "SG", "SGD", ClaimStatus.UNDER_REVIEW, 1, new BigDecimal("135.00")),    // 100.00 USD
                new RemoteStaffStatusTotal(7L, "JP", "JPY", ClaimStatus.MORE_INFO_REQUIRED, 1, new BigDecimal("15000.00")), // 100.00 USD
                new RemoteStaffStatusTotal(7L, "JP", "JPY", ClaimStatus.APPROVED, 1, new BigDecimal("150000.00")))));       // not open
        when(assessmentRepository.findStaffNames()).thenReturn(List.of());

        assertThat(dashboardService.staffWorkload(null).staff().get(0).openExposure()).isEqualByComparingTo("200.00");
    }

    @Test
    void aStaffMemberWithOnlyFinishedClaimsHasNoOpenWorkAndNoExposure() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());
        when(assessmentRepository.findStaffNames()).thenReturn(List.of());

        StaffWorkload carol = dashboardService.staffWorkload(null).staff().get(2);

        assertThat(carol.staffId()).isEqualTo(9L);
        assertThat(carol.staffName()).isNull();
        assertThat(carol.openClaims()).isZero();
        assertThat(carol.settled()).isEqualTo(2);
        assertThat(carol.totalAssigned()).isEqualTo(2);
        assertThat(carol.openExposure()).isEqualByComparingTo("0.00");
    }

    @Test
    void equallyBusyClaimsStaffAreOrderedById() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(
                List.of(),
                List.of(staffTotal(12L, "SG", "SGD", ClaimStatus.UNDER_REVIEW, 1, "10.00"), staffTotal(5L, "SG", "SGD", ClaimStatus.UNDER_REVIEW, 1, "10.00"))));
        when(assessmentRepository.findStaffNames()).thenReturn(List.of());

        assertThat(dashboardService.staffWorkload(null).staff()).extracting(StaffWorkload::staffId).containsExactly(5L, 12L);
    }

    @Test
    void workloadWithNoAssignedClaimsListsNoStaff() {
        when(claimServiceClient.getStatistics()).thenReturn(new RemoteStatistics(
                List.of(new RemoteStatusTotal("SG", "SGD", ClaimStatus.SUBMITTED, 4, new BigDecimal("400.00"))), List.of()));
        when(assessmentRepository.findStaffNames()).thenReturn(List.of());

        StaffWorkloadResponse workload = dashboardService.staffWorkload(null);

        assertThat(workload.staff()).isEmpty();
        assertThat(workload.unassignedClaims()).isEqualTo(4);
    }

    // ---- liability exposure -----------------------------------------------------------------------------------

    @Test
    void liabilityExposureAppliesTheFormulaToClaimServicesTotals() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        LiabilityExposureResponse exposure = dashboardService.liabilityExposure(null);

        assertThat(exposure.totalLiabilityExposure()).isEqualByComparingTo("6900.50");
        assertThat(exposure.exposedClaims()).isEqualTo(11);
        assertThat(exposure.excludedSettledAmount()).isEqualByComparingTo("900.00");
        assertThat(exposure.reportingCurrency()).isEqualTo("USD");
        assertThat(exposure.market()).isNull();
        assertThat(exposure.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void liabilityExposureIsListedPerMarketInItsOwnCurrencyToo() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        LiabilityExposureResponse exposure = dashboardService.liabilityExposure(null);

        assertThat(exposure.byMarket()).extracting(MarketExposure::market).containsExactly("JP", "SG");
        MarketExposure japan = exposure.byMarket().get(0);
        assertThat(japan.currency()).isEqualTo("JPY");
        assertThat(japan.claimCount()).isEqualTo(4);
        assertThat(japan.exposure()).isEqualByComparingTo("255075.00");
        assertThat(japan.exposureInReportingCurrency()).isEqualByComparingTo("1700.50");
        MarketExposure singapore = exposure.byMarket().get(1);
        assertThat(singapore.currency()).isEqualTo("SGD");
        assertThat(singapore.claimCount()).isEqualTo(7);
        assertThat(singapore.exposure()).isEqualByComparingTo("7020.00");
        assertThat(singapore.exposureInReportingCurrency()).isEqualByComparingTo("5200.00");
        assertThat(japan.exposureInReportingCurrency().add(singapore.exposureInReportingCurrency()))
                .isEqualByComparingTo(exposure.totalLiabilityExposure());
    }

    @Test
    void liabilityExposureCanBeLimitedToOneMarket() {
        when(claimServiceClient.getStatistics()).thenReturn(sampleStatistics());

        LiabilityExposureResponse exposure = dashboardService.liabilityExposure("JP");

        assertThat(exposure.market()).isEqualTo("JP");
        assertThat(exposure.totalLiabilityExposure()).isEqualByComparingTo("1700.50");
        assertThat(exposure.exposedClaims()).isEqualTo(4);
        assertThat(exposure.excludedSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(exposure.byMarket()).extracting(MarketExposure::market).containsExactly("JP", "SG");
    }

    @Test
    void anOutageOfClaimServiceFailsTheDashboardInsteadOfShowingStaleNumbers() {
        when(claimServiceClient.getStatistics()).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        assertThatThrownBy(() -> dashboardService.statusSummary(null)).isInstanceOf(ClaimServiceUnavailableException.class);
        assertThatThrownBy(() -> dashboardService.liabilityExposure(null)).isInstanceOf(ClaimServiceUnavailableException.class);
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    private static RemoteStatistics sampleStatistics() {
        return new RemoteStatistics(
                List.of(
                        statusTotal("SG", "SGD", ClaimStatus.SUBMITTED, 2, "270.00"),
                        statusTotal("JP", "JPY", ClaimStatus.SUBMITTED, 1, "15000.00"),
                        statusTotal("SG", "SGD", ClaimStatus.UNDER_REVIEW, 2, "2700.00"),
                        statusTotal("JP", "JPY", ClaimStatus.MORE_INFO_REQUIRED, 1, "75075.00"),
                        statusTotal("SG", "SGD", ClaimStatus.APPROVED, 3, "4050.00"),
                        statusTotal("JP", "JPY", ClaimStatus.APPROVED, 1, "150000.00"),
                        statusTotal("JP", "JPY", ClaimStatus.REJECTED, 1, "15000.00"),
                        statusTotal("SG", "SGD", ClaimStatus.SETTLED, 2, "1215.00")),
                List.of(
                        staffTotal(7L, "SG", "SGD", ClaimStatus.UNDER_REVIEW, 2, "2700.00"),
                        staffTotal(7L, "SG", "SGD", ClaimStatus.APPROVED, 3, "4050.00"),
                        staffTotal(8L, "JP", "JPY", ClaimStatus.MORE_INFO_REQUIRED, 1, "75075.00"),
                        staffTotal(8L, "JP", "JPY", ClaimStatus.APPROVED, 1, "150000.00"),
                        staffTotal(8L, "JP", "JPY", ClaimStatus.REJECTED, 1, "15000.00"),
                        staffTotal(9L, "SG", "SGD", ClaimStatus.SETTLED, 2, "1215.00")));
    }

    private static RemoteStatusTotal statusTotal(String market, String currency, ClaimStatus status, long count, String amount) {
        return new RemoteStatusTotal(market, currency, status, count, new BigDecimal(amount));
    }

    private static RemoteStaffStatusTotal staffTotal(Long staffId, String market, String currency, ClaimStatus status,
                                                         long count, String amount) {
        return new RemoteStaffStatusTotal(staffId, market, currency, status, count, new BigDecimal(amount));
    }

    private static ClaimAssessmentRepository.StaffName staffName(Long id, String name) {
        return new ClaimAssessmentRepository.StaffName() {
            @Override
            public Long getStaffId() {
                return id;
            }

            @Override
            public String getStaffName() {
                return name;
            }
        };
    }
}
