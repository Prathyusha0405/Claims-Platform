package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.ClaimType;
import com.chubb.claims.assessment.client.Market;
import com.chubb.claims.assessment.client.StatisticsScope;
import com.chubb.claims.assessment.config.SecurityConfig;
import com.chubb.claims.assessment.controller.DashboardController;
import com.chubb.claims.assessment.dto.ClaimTypeExposure;
import com.chubb.claims.assessment.dto.ClaimTypeSummary;
import com.chubb.claims.assessment.dto.ClaimsOverTimeResponse;
import com.chubb.claims.assessment.dto.ClaimsPeriod;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.MarketExposure;
import com.chubb.claims.assessment.dto.MarketSummary;
import com.chubb.claims.assessment.dto.StaffWorkload;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusExposure;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.security.AuthenticatedUser;
import com.chubb.claims.assessment.security.JwtService;
import com.chubb.claims.assessment.security.Role;
import com.chubb.claims.assessment.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final List<String> ENDPOINTS = List.of(
            "/api/dashboard/status-summary", "/api/dashboard/staff-workload", "/api/dashboard/liability-exposure",
            "/api/dashboard/claims-over-time");

    private static final AuthenticatedUser MANAGER = new AuthenticatedUser(8L, "manager@chubb.com", "Demo Manager", Role.MANAGER);
    private static final AuthenticatedUser CLAIMS_STAFF = new AuthenticatedUser(7L, "staff@chubb.com", "Demo Claims Staff", Role.CLAIMS_STAFF);
    private static final AuthenticatedUser CLAIMANT = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void statusSummaryShowsTheCounts() throws Exception {
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            byStatus.put(status, 1L);
        }
        byStatus.put(ClaimStatus.SUBMITTED, 3L);
        when(dashboardService.statusSummary(StatisticsScope.all())).thenReturn(new StatusSummaryResponse(13, 6, 4, 1, 2, byStatus,
                null, null, null, null,
                List.of(new MarketSummary("JP", 4, 2, 1, 1, 0), new MarketSummary("SG", 9, 4, 3, 0, 2)),
                List.of(new ClaimTypeSummary(ClaimType.AUTO, 9, 5, 3, 0, 1), new ClaimTypeSummary(ClaimType.HOME, 4, 1, 1, 1, 1)), NOW));

        mockMvc.perform(get("/api/dashboard/status-summary").with(as(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(13))
                .andExpect(jsonPath("$.pendingClaims").value(6))
                .andExpect(jsonPath("$.approvedClaims").value(4))
                .andExpect(jsonPath("$.rejectedClaims").value(1))
                .andExpect(jsonPath("$.settledClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SUBMITTED").value(3))
                .andExpect(jsonPath("$.claimsByStatus.SETTLED").value(1))
                .andExpect(jsonPath("$.market").doesNotExist())
                .andExpect(jsonPath("$.byMarket.length()").value(2))
                .andExpect(jsonPath("$.byMarket[1].market").value("SG"))
                .andExpect(jsonPath("$.byMarket[1].totalClaims").value(9))
                .andExpect(jsonPath("$.byMarket[1].pendingClaims").value(4))
                .andExpect(jsonPath("$.claimType").doesNotExist())
                .andExpect(jsonPath("$.fromDate").doesNotExist())
                .andExpect(jsonPath("$.toDate").doesNotExist())
                .andExpect(jsonPath("$.byClaimType.length()").value(2))
                .andExpect(jsonPath("$.byClaimType[0].claimType").value("AUTO"))
                .andExpect(jsonPath("$.byClaimType[0].totalClaims").value(9))
                .andExpect(jsonPath("$.byClaimType[1].claimType").value("HOME"))
                .andExpect(jsonPath("$.byClaimType[1].rejectedClaims").value(1))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-22T06:00:00Z"));
    }

    @Test
    void staffWorkloadShowsEachStaffMember() throws Exception {
        when(dashboardService.staffWorkload(StatisticsScope.all())).thenReturn(new StaffWorkloadResponse("USD", null, null, null, null, 3, List.of(
                new StaffWorkload(7L, "Alice Staff", 2, 2, 0, 3, 0, 0, 5, new BigDecimal("2000.00"))), NOW));

        mockMvc.perform(get("/api/dashboard/staff-workload").with(as(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingCurrency").value("USD"))
                .andExpect(jsonPath("$.unassignedClaims").value(3))
                .andExpect(jsonPath("$.staff[0].staffId").value(7))
                .andExpect(jsonPath("$.staff[0].staffName").value("Alice Staff"))
                .andExpect(jsonPath("$.staff[0].openClaims").value(2))
                .andExpect(jsonPath("$.staff[0].totalAssigned").value(5))
                .andExpect(jsonPath("$.staff[0].openExposure").value(2000.00));
    }

    @Test
    void liabilityExposureShowsTheTotalAndTheFormula() throws Exception {
        when(dashboardService.liabilityExposure(StatisticsScope.all())).thenReturn(new LiabilityExposureResponse("USD", null, null, null, null,
                new BigDecimal("6900.50"), 11, new BigDecimal("900.00"), "SUM(claimAmount) WHERE status != SETTLED",
                List.of(new StatusExposure(ClaimStatus.SUBMITTED, 3, new BigDecimal("300.00"), true),
                        new StatusExposure(ClaimStatus.SETTLED, 2, new BigDecimal("900.00"), false)),
                List.of(new MarketExposure("JP", "JPY", new BigDecimal("150"), 4, new BigDecimal("255075.00"), new BigDecimal("1700.50"))),
                List.of(new ClaimTypeExposure(ClaimType.AUTO, 8, new BigDecimal("5700.50")),
                        new ClaimTypeExposure(ClaimType.HOME, 3, new BigDecimal("1200.00"))),
                NOW));

        mockMvc.perform(get("/api/dashboard/liability-exposure").with(as(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiabilityExposure").value(6900.50))
                .andExpect(jsonPath("$.exposedClaims").value(11))
                .andExpect(jsonPath("$.excludedSettledAmount").value(900.00))
                .andExpect(jsonPath("$.formula").value("SUM(claimAmount) WHERE status != SETTLED"))
                .andExpect(jsonPath("$.byStatus[1].status").value("SETTLED"))
                .andExpect(jsonPath("$.byStatus[1].includedInExposure").value(false))
                .andExpect(jsonPath("$.reportingCurrency").value("USD"))
                .andExpect(jsonPath("$.byMarket[0].market").value("JP"))
                .andExpect(jsonPath("$.byMarket[0].currency").value("JPY"))
                .andExpect(jsonPath("$.byMarket[0].rate").value(150))
                .andExpect(jsonPath("$.byMarket[0].exposure").value(255075.00))
                .andExpect(jsonPath("$.byMarket[0].exposureInReportingCurrency").value(1700.50))
                .andExpect(jsonPath("$.byClaimType[0].claimType").value("AUTO"))
                .andExpect(jsonPath("$.byClaimType[0].claimCount").value(8))
                .andExpect(jsonPath("$.byClaimType[0].exposureInReportingCurrency").value(5700.50))
                .andExpect(jsonPath("$.byClaimType[1].claimType").value("HOME"));
    }

    @Test
    void claimsOverTimeShowsEachPeriodWithItsPendingAndDecidedTotals() throws Exception {
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            byStatus.put(status, 0L);
        }
        byStatus.put(ClaimStatus.SUBMITTED, 2L);
        byStatus.put(ClaimStatus.APPROVED, 3L);
        when(dashboardService.claimsOverTime(StatisticsScope.all())).thenReturn(new ClaimsOverTimeResponse(
                ClaimsOverTimeResponse.Granularity.WEEK, null, null, null, null,
                List.of(new ClaimsPeriod(LocalDate.of(2026, 8, 3), 5, 2, 3, byStatus)), NOW));

        mockMvc.perform(get("/api/dashboard/claims-over-time").with(as(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("WEEK"))
                .andExpect(jsonPath("$.market").doesNotExist())
                .andExpect(jsonPath("$.periods.length()").value(1))
                .andExpect(jsonPath("$.periods[0].start").value("2026-08-03"))
                .andExpect(jsonPath("$.periods[0].totalClaims").value(5))
                .andExpect(jsonPath("$.periods[0].pendingClaims").value(2))
                .andExpect(jsonPath("$.periods[0].decidedClaims").value(3))
                .andExpect(jsonPath("$.periods[0].claimsByStatus.SUBMITTED").value(2))
                .andExpect(jsonPath("$.periods[0].claimsByStatus.SETTLED").value(0))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-22T06:00:00Z"));
    }

    @Test
    void theMarketFilterIsPassedToEveryDashboard() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).param("market", "JP").with(as(MANAGER))).andExpect(status().isOk());
        }

        verify(dashboardService).statusSummary(StatisticsScope.ofMarket("JP"));
        verify(dashboardService).staffWorkload(StatisticsScope.ofMarket("JP"));
        verify(dashboardService).liabilityExposure(StatisticsScope.ofMarket("JP"));
        verify(dashboardService).claimsOverTime(StatisticsScope.ofMarket("JP"));
    }

    @Test
    void marketClaimTypeAndDatesAreAllPassedToEveryDashboard() throws Exception {
        StatisticsScope scope = new StatisticsScope(Market.SG, ClaimType.HOME, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31));

        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).param("market", "sg").param("claimType", "home")
                            .param("fromDate", "2026-08-01").param("toDate", "2026-10-31").with(as(MANAGER)))
                    .andExpect(status().isOk());
        }

        verify(dashboardService).statusSummary(scope);
        verify(dashboardService).staffWorkload(scope);
        verify(dashboardService).liabilityExposure(scope);
        verify(dashboardService).claimsOverTime(scope);
    }

    @Test
    void noFiltersMeansEveryClaim() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).with(as(MANAGER))).andExpect(status().isOk());
        }

        verify(dashboardService).claimsOverTime(StatisticsScope.all());
        verify(dashboardService).statusSummary(StatisticsScope.all());
    }

    @Test
    void anUnknownClaimTypeABackwardsRangeOrABadDateIsA400AndNeverReachesTheService() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).param("claimType", "BOAT").with(as(MANAGER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unknown claimType 'BOAT'; allowed values: "
                            + "[AUTO, HOME, HEALTH, TRAVEL, PERSONAL_ACCIDENT, OTHER]"));
            mockMvc.perform(get(endpoint).param("fromDate", "2026-10-31").param("toDate", "2026-08-01").with(as(MANAGER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
            mockMvc.perform(get(endpoint).param("fromDate", "last week").with(as(MANAGER)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(dashboardService);
    }

    @Test
    void anUnknownMarketIsA400WithTheAllowedValues() throws Exception {
        mockMvc.perform(get("/api/dashboard/liability-exposure").param("market", "XX").with(as(MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown market 'XX'; allowed values: [AU, HK, JP, MY, NZ, SG]"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    void onlyManagersMayViewTheDashboards() throws Exception {
        for (AuthenticatedUser caller : List.of(CLAIMS_STAFF, CLAIMANT)) {
            for (String endpoint : ENDPOINTS) {
                mockMvc.perform(get(endpoint).with(as(caller)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403));
            }
        }
        verifyNoInteractions(dashboardService);
    }

    @Test
    void theDashboardsNeedAToken() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void whenClaimServiceIsDownTheDashboardAnswers503NotStaleNumbers() throws Exception {
        when(dashboardService.liabilityExposure(StatisticsScope.all())).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        mockMvc.perform(get("/api/dashboard/liability-exposure").with(as(MANAGER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Claim service is unavailable"));
    }

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, "the-token", user.authorities()));
    }
}
