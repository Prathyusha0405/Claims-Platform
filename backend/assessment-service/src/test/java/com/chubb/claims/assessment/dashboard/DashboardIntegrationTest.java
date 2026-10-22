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

/** The three dashboards end to end: admin token in, claim-service aggregates over HTTP, business metrics out. */
class DashboardIntegrationTest extends AbstractStubbedClaimServiceTest {

    private static final String STATISTICS_PATH = "/api/internal/claims/statistics";

    @Autowired
    private ClaimAssessmentRepository assessmentRepository;

    @Test
    void statusSummaryCountsTheClaims() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        String adminToken = token(randomId(), Role.ADMIN);

        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(13))
                .andExpect(jsonPath("$.pendingClaims").value(6))
                .andExpect(jsonPath("$.approvedClaims").value(4))
                .andExpect(jsonPath("$.rejectedClaims").value(1))
                .andExpect(jsonPath("$.settledClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SUBMITTED").value(3))
                .andExpect(jsonPath("$.claimsByStatus.MORE_INFO_REQUIRED").value(1));

        // The admin's own token was forwarded; claim-service authorizes the admin directly.
        assertThat(CLAIM_SERVICE.lastRequest().path()).isEqualTo(STATISTICS_PATH);
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isEqualTo("Bearer " + adminToken);
    }

    @Test
    void liabilityExposureExcludesSettledClaims() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());

        mockMvc.perform(get("/api/dashboard/liability-exposure").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiabilityExposure").value(6900.50))
                .andExpect(jsonPath("$.exposedClaims").value(11))
                .andExpect(jsonPath("$.excludedSettledAmount").value(900.00))
                .andExpect(jsonPath("$.formula").value("SUM(claimAmount) WHERE status != SETTLED"))
                .andExpect(jsonPath("$.byStatus.length()").value(6))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'SETTLED')].includedInExposure").value(false))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'APPROVED')].includedInExposure").value(true))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'APPROVED')].amount").value(4000.00));
    }

    @Test
    void officerWorkloadListsEachOfficerWithNamesFromTheirAssessments() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, ClaimJson.statistics());
        assessmentRepository.save(new ClaimAssessment(randomId(), "CLM-2026-900001", 7L, "Alice Officer", Instant.now()));
        assessmentRepository.save(new ClaimAssessment(randomId(), "CLM-2026-900002", 7L, "Alice Officer", Instant.now()));

        mockMvc.perform(get("/api/dashboard/officer-workload").header(HttpHeaders.AUTHORIZATION, bearer(randomId(), Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedClaims").value(3))
                .andExpect(jsonPath("$.officers.length()").value(3))
                .andExpect(jsonPath("$.officers[0].officerId").value(7))
                .andExpect(jsonPath("$.officers[0].officerName").value("Alice Officer"))
                .andExpect(jsonPath("$.officers[0].openClaims").value(2))
                .andExpect(jsonPath("$.officers[0].approved").value(3))
                .andExpect(jsonPath("$.officers[0].openExposure").value(2000.00))
                .andExpect(jsonPath("$.officers[1].officerId").value(8))
                .andExpect(jsonPath("$.officers[1].awaitingInformation").value(1))
                .andExpect(jsonPath("$.officers[1].openExposure").value(500.50))
                .andExpect(jsonPath("$.officers[2].officerId").value(9))
                .andExpect(jsonPath("$.officers[2].openClaims").value(0));
    }

    @Test
    void whenClaimServiceFailsTheDashboardsAnswer503() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 500, ClaimJson.error(500, "boom"));
        String admin = bearer(randomId(), Role.ADMIN);

        for (String endpoint : new String[]{"status-summary", "officer-workload", "liability-exposure"}) {
            mockMvc.perform(get("/api/dashboard/" + endpoint).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503));
        }
    }

    @Test
    void anEmptyPlatformShowsZeros() throws Exception {
        CLAIM_SERVICE.on("GET", STATISTICS_PATH, 200, "{\"byStatus\":[],\"byOfficer\":[]}");
        String admin = bearer(randomId(), Role.ADMIN);

        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalClaims").value(0))
                .andExpect(jsonPath("$.pendingClaims").value(0));
        mockMvc.perform(get("/api/dashboard/liability-exposure").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalLiabilityExposure").value(0.00))
                .andExpect(jsonPath("$.exposedClaims").value(0));
        mockMvc.perform(get("/api/dashboard/officer-workload").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.officers.length()").value(0))
                .andExpect(jsonPath("$.unassignedClaims").value(0));
    }
}
