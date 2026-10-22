package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.config.SecurityConfig;
import com.chubb.claims.assessment.controller.DashboardController;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.OfficerWorkload;
import com.chubb.claims.assessment.dto.OfficerWorkloadResponse;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
            "/api/dashboard/status-summary", "/api/dashboard/officer-workload", "/api/dashboard/liability-exposure");

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(8L, "admin@chubb.com", "Demo Admin", Role.ADMIN);
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);
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
        when(dashboardService.statusSummary()).thenReturn(new StatusSummaryResponse(13, 6, 4, 1, 2, byStatus, NOW));

        mockMvc.perform(get("/api/dashboard/status-summary").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(13))
                .andExpect(jsonPath("$.pendingClaims").value(6))
                .andExpect(jsonPath("$.approvedClaims").value(4))
                .andExpect(jsonPath("$.rejectedClaims").value(1))
                .andExpect(jsonPath("$.settledClaims").value(2))
                .andExpect(jsonPath("$.claimsByStatus.SUBMITTED").value(3))
                .andExpect(jsonPath("$.claimsByStatus.SETTLED").value(1))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-22T06:00:00Z"));
    }

    @Test
    void officerWorkloadShowsEachOfficer() throws Exception {
        when(dashboardService.officerWorkload()).thenReturn(new OfficerWorkloadResponse(3, List.of(
                new OfficerWorkload(7L, "Alice Officer", 2, 2, 0, 3, 0, 0, 5, new BigDecimal("2000.00"))), NOW));

        mockMvc.perform(get("/api/dashboard/officer-workload").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedClaims").value(3))
                .andExpect(jsonPath("$.officers[0].officerId").value(7))
                .andExpect(jsonPath("$.officers[0].officerName").value("Alice Officer"))
                .andExpect(jsonPath("$.officers[0].openClaims").value(2))
                .andExpect(jsonPath("$.officers[0].totalAssigned").value(5))
                .andExpect(jsonPath("$.officers[0].openExposure").value(2000.00));
    }

    @Test
    void liabilityExposureShowsTheTotalAndTheFormula() throws Exception {
        when(dashboardService.liabilityExposure()).thenReturn(new LiabilityExposureResponse(
                new BigDecimal("6900.50"), 11, new BigDecimal("900.00"), "SUM(claimAmount) WHERE status != SETTLED",
                List.of(new StatusExposure(ClaimStatus.SUBMITTED, 3, new BigDecimal("300.00"), true),
                        new StatusExposure(ClaimStatus.SETTLED, 2, new BigDecimal("900.00"), false)), NOW));

        mockMvc.perform(get("/api/dashboard/liability-exposure").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiabilityExposure").value(6900.50))
                .andExpect(jsonPath("$.exposedClaims").value(11))
                .andExpect(jsonPath("$.excludedSettledAmount").value(900.00))
                .andExpect(jsonPath("$.formula").value("SUM(claimAmount) WHERE status != SETTLED"))
                .andExpect(jsonPath("$.byStatus[1].status").value("SETTLED"))
                .andExpect(jsonPath("$.byStatus[1].includedInExposure").value(false));
    }

    @Test
    void onlyAdminsMayViewTheDashboards() throws Exception {
        for (AuthenticatedUser caller : List.of(OFFICER, CLAIMANT)) {
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
        when(dashboardService.liabilityExposure()).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        mockMvc.perform(get("/api/dashboard/liability-exposure").with(as(ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Claim service is unavailable"));
    }

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, "the-token", user.authorities()));
    }
}
