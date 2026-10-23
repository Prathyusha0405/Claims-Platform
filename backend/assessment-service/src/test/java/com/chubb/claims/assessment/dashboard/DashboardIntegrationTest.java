package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.entity.ClaimAssessment;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.security.Role;
import com.chubb.claims.assessment.support.AbstractStubbedClaimServiceTest;
import com.chubb.claims.assessment.support.ClaimJson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The three dashboards end to end: manager token in, claim-service aggregates over HTTP, business metrics out. */
class DashboardIntegrationTest extends AbstractStubbedClaimServiceTest {

    private static final String STATISTICS_PATH = "/api/internal/claims/statistics";
    private static final String TREND_PATH = "/api/internal/claims/trend";

    @Autowired
    private ClaimAssessmentRepository assessmentRepository;

    @Test
    void statusSummaryCountsTheClaims() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        String adminToken = token(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(13))
                .andExpect(jsonPath("$.pendingClaims").value(6))
                .andExpect(jsonPath("$.approvedClaims").value(4))
                .andExpect(jsonPath("$.rejectedClaims").value(1))
                .andExpect(jsonPath("$.settledClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SUBMITTED").value(3))
                .andExpect(jsonPath("$.claimsByStatus.MORE_INFO_REQUIRED").value(1))
                .andExpect(jsonPath("$.market").doesNotExist())
                .andExpect(jsonPath("$.byMarket.length()").value(2))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'SG')].totalClaims").value(9))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'JP')].totalClaims").value(4));

        // The manager's own token was forwarded; claim-service authorizes the manager directly.
        assertThat(CLAIM_SERVICE.lastRequest().path()).isEqualTo(STATISTICS_PATH);
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isEqualTo("Bearer " + adminToken);
    }

    @Test
    void liabilityExposureExcludesSettledClaims() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());

        mockMvc.perform(get("/api/dashboard/liability-exposure").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiabilityExposure").value(6900.50))
                .andExpect(jsonPath("$.exposedClaims").value(11))
                .andExpect(jsonPath("$.excludedSettledAmount").value(900.00))
                .andExpect(jsonPath("$.formula").value("SUM(claimAmount) WHERE status != SETTLED"))
                .andExpect(jsonPath("$.byStatus.length()").value(6))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'SETTLED')].includedInExposure").value(false))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'APPROVED')].includedInExposure").value(true))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'APPROVED')].amount").value(4000.00))
                // claim-service reports SGD and JPY; the dashboard converts them to USD at the configured rates
                .andExpect(jsonPath("$.reportingCurrency").value("USD"))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'JP')].currency").value("JPY"))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'JP')].exposure").value(255075.00))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'JP')].exposureInReportingCurrency").value(1700.50))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'SG')].currency").value("SGD"))
                .andExpect(jsonPath("$.byMarket[?(@.market == 'SG')].exposureInReportingCurrency").value(5200.00));
    }

    @Test
    void everyDashboardCanBeLimitedToOneMarket() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        String admin = bearer(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/status-summary").param("market", "JP").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("JP"))
                .andExpect(jsonPath("$.totalClaims").value(4))
                .andExpect(jsonPath("$.pendingClaims").value(2))
                .andExpect(jsonPath("$.byMarket.length()").value(2));
        mockMvc.perform(get("/api/dashboard/liability-exposure").param("market", "jp").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.market").value("JP"))
                .andExpect(jsonPath("$.totalLiabilityExposure").value(1700.50))
                .andExpect(jsonPath("$.exposedClaims").value(4))
                .andExpect(jsonPath("$.byMarket.length()").value(2));
        mockMvc.perform(get("/api/dashboard/staff-workload").param("market", "JP").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.market").value("JP"))
                .andExpect(jsonPath("$.unassignedClaims").value(1))
                .andExpect(jsonPath("$.staff.length()").value(1))
                .andExpect(jsonPath("$.staff[0].staffId").value(8))
                .andExpect(jsonPath("$.staff[0].openExposure").value(500.50));
    }

    @Test
    void theBreakdownsByClaimTypeAddUpToTheSameClaimsAndTheSameExposure() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        String admin = bearer(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byClaimType.length()").value(2))
                .andExpect(jsonPath("$.byClaimType[0].claimType").value("AUTO"))
                .andExpect(jsonPath("$.byClaimType[0].totalClaims").value(9))
                .andExpect(jsonPath("$.byClaimType[0].pendingClaims").value(5))
                .andExpect(jsonPath("$.byClaimType[1].claimType").value("HOME"))
                .andExpect(jsonPath("$.byClaimType[1].totalClaims").value(4));
        mockMvc.perform(get("/api/dashboard/liability-exposure").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiabilityExposure").value(6900.50))
                .andExpect(jsonPath("$.byClaimType[0].claimType").value("AUTO"))
                .andExpect(jsonPath("$.byClaimType[0].claimCount").value(8))
                .andExpect(jsonPath("$.byClaimType[0].exposureInReportingCurrency").value(5700.50))
                .andExpect(jsonPath("$.byClaimType[1].claimType").value("HOME"))
                .andExpect(jsonPath("$.byClaimType[1].exposureInReportingCurrency").value(1200.00));
    }

    @Test
    void theClaimTypeAndDatesTravelToClaimServiceButTheMarketDoesNot() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        String admin = bearer(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/status-summary").param("market", "JP").param("claimType", "HOME")
                        .param("fromDate", "2026-08-01").param("toDate", "2026-10-31").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("JP"))
                .andExpect(jsonPath("$.claimType").value("HOME"))
                .andExpect(jsonPath("$.fromDate").value("2026-08-01"))
                .andExpect(jsonPath("$.toDate").value("2026-10-31"));

        // The per-market comparison must keep seeing every market, so claim-service is never told the market.
        assertThat(CLAIM_SERVICE.lastRequest().query()).contains("claimType=HOME", "fromDate=2026-08-01", "toDate=2026-10-31")
                .doesNotContain("market");
    }

    @Test
    void claimsOverTimeComesFromClaimServicesTrendWithThePendingAndDecidedTotals() throws Exception {
        CLAIM_SERVICE.on("GET", TREND_PATH, 200, ClaimJson.trend());
        String admin = bearer(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/claims-over-time").param("market", "SG").param("claimType", "AUTO")
                        .param("fromDate", "2026-08-01").param("toDate", "2026-08-31").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("WEEK"))
                .andExpect(jsonPath("$.market").value("SG"))
                .andExpect(jsonPath("$.claimType").value("AUTO"))
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[0].start").value("2026-08-03"))
                .andExpect(jsonPath("$.periods[0].totalClaims").value(6))
                .andExpect(jsonPath("$.periods[0].pendingClaims").value(3))
                .andExpect(jsonPath("$.periods[0].decidedClaims").value(3))
                .andExpect(jsonPath("$.periods[1].totalClaims").value(7))
                .andExpect(jsonPath("$.periods[1].pendingClaims").value(3))
                .andExpect(jsonPath("$.periods[1].claimsByStatus.MORE_INFO_REQUIRED").value(1));

        // Unlike the statistics, the trend is asked about the market too, and the manager's own token is forwarded.
        assertThat(CLAIM_SERVICE.lastRequest().path()).isEqualTo(TREND_PATH);
        assertThat(CLAIM_SERVICE.lastRequest().query()).contains("market=SG", "claimType=AUTO", "fromDate=2026-08-01", "toDate=2026-08-31");
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isEqualTo(admin);
    }

    @Test
    void claimsOverTimeWithNothingToShowIsAnEmptyList() throws Exception {
        CLAIM_SERVICE.on("GET", TREND_PATH, 200, "{\"granularity\":\"WEEK\",\"periods\":[]}");

        mockMvc.perform(get("/api/dashboard/claims-over-time").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods.length()").value(0))
                .andExpect(jsonPath("$.granularity").value("WEEK"));
    }

    @Test
    void claimsOverTimeAnswers503WhenClaimServiceFails() throws Exception {
        CLAIM_SERVICE.on("GET", TREND_PATH, 500, ClaimJson.error(500, "boom"));

        mockMvc.perform(get("/api/dashboard/claims-over-time").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.MANAGER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void badFiltersAreRefusedBeforeClaimServiceIsAsked() throws Exception {
        String admin = bearer(randomId(), Role.MANAGER);

        for (String endpoint : new String[]{"status-summary", "staff-workload", "liability-exposure", "claims-over-time"}) {
            mockMvc.perform(get("/api/dashboard/" + endpoint).param("claimType", "BOAT").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unknown claimType 'BOAT'")));
            mockMvc.perform(get("/api/dashboard/" + endpoint).param("fromDate", "2026-10-31").param("toDate", "2026-08-01")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isBadRequest());
        }
        assertThat(CLAIM_SERVICE.requests()).isEmpty();
    }

    @Test
    void anUnknownMarketIsRefusedBeforeClaimServiceIsAsked() throws Exception {
        String admin = bearer(randomId(), Role.MANAGER);

        for (String endpoint : new String[]{"status-summary", "staff-workload", "liability-exposure", "claims-over-time"}) {
            mockMvc.perform(get("/api/dashboard/" + endpoint).param("market", "XX").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unknown market 'XX'; allowed values: [AU, HK, JP, MY, NZ, SG]"));
        }
    }

    @Test
    void staffWorkloadListsEachStaffMemberWithNamesFromTheirAssessments() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        assessmentRepository.save(new ClaimAssessment(randomId(), "CLM-2026-900001", 7L, "Alice Staff", Instant.now()));
        assessmentRepository.save(new ClaimAssessment(randomId(), "CLM-2026-900002", 7L, "Alice Staff", Instant.now()));

        mockMvc.perform(get("/api/dashboard/staff-workload").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingCurrency").value("USD"))
                .andExpect(jsonPath("$.unassignedClaims").value(3))
                .andExpect(jsonPath("$.staff.length()").value(3))
                .andExpect(jsonPath("$.staff[0].staffId").value(7))
                .andExpect(jsonPath("$.staff[0].staffName").value("Alice Staff"))
                .andExpect(jsonPath("$.staff[0].openClaims").value(2))
                .andExpect(jsonPath("$.staff[0].approved").value(3))
                .andExpect(jsonPath("$.staff[0].openExposure").value(2000.00))
                .andExpect(jsonPath("$.staff[1].staffId").value(8))
                .andExpect(jsonPath("$.staff[1].awaitingInformation").value(1))
                .andExpect(jsonPath("$.staff[1].openExposure").value(500.50))
                .andExpect(jsonPath("$.staff[2].staffId").value(9))
                .andExpect(jsonPath("$.staff[2].openClaims").value(0));
    }

    @Test
    void whenClaimServiceFailsTheDashboardsAnswer503() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 500, ClaimJson.error(500, "boom"));
        String admin = bearer(randomId(), Role.MANAGER);

        for (String endpoint : new String[]{"status-summary", "staff-workload", "liability-exposure"}) {
            mockMvc.perform(get("/api/dashboard/" + endpoint).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503));
        }
    }

    @Test
    void anEmptyPlatformShowsZeros() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, "{\"byStatus\":[],\"byStaff\":[]}");
        String admin = bearer(randomId(), Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalClaims").value(0))
                .andExpect(jsonPath("$.pendingClaims").value(0));
        mockMvc.perform(get("/api/dashboard/liability-exposure").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalLiabilityExposure").value(0.00))
                .andExpect(jsonPath("$.exposedClaims").value(0));
        mockMvc.perform(get("/api/dashboard/staff-workload").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.staff.length()").value(0))
                .andExpect(jsonPath("$.unassignedClaims").value(0));
    }
}
