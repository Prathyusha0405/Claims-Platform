package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The statistics and trend endpoints behind the manager dashboard, with real JWTs and a real database. The claims are
 * submitted in March 2001, so a date window isolates them from whatever the other tests have created; every figure asserted
 * here is therefore exact.
 * <pre>
 * 2001-03-05 (Mon)  SG  AUTO  SUBMITTED         100.00
 * 2001-03-07 (Wed)  SG  HOME  UNDER_REVIEW      200.00  (staff 91)
 * 2001-03-14 (Wed)  JP  AUTO  APPROVED        30000.00  (staff 91)
 * 2001-03-28 (Wed)  SG  AUTO  SUBMITTED         400.00
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class StatisticsApiIntegrationTest {

    private static final String WINDOW_FROM = "2001-03-01";
    private static final String WINDOW_TO = "2001-03-31";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /** The claims are written once per JVM; the database outlives the individual tests. */
    private static boolean seeded;

    private void seed() {
        if (seeded) {
            return;
        }
        claimRepository.save(claim("CLM-2001-000001", "100.00", Market.SG, ClaimType.AUTO, "2001-03-05T09:00:00Z", null, null));
        claimRepository.save(claim("CLM-2001-000002", "200.00", Market.SG, ClaimType.HOME, "2001-03-07T09:00:00Z", 91L, ClaimStatus.UNDER_REVIEW));
        claimRepository.save(claim("CLM-2001-000003", "30000", Market.JP, ClaimType.AUTO, "2001-03-14T09:00:00Z", 91L, ClaimStatus.APPROVED));
        claimRepository.saveAndFlush(claim("CLM-2001-000004", "400.00", Market.SG, ClaimType.AUTO, "2001-03-28T09:00:00Z", null, null));
        seeded = true;
    }

    // ---- statistics -----------------------------------------------------------------------------------------

    @Test
    void theWindowIsolatesTheClaimsAndEveryBreakdownAddsUp() throws Exception {
        seed();

        mockMvc.perform(get("/api/internal/claims/statistics").param("fromDate", WINDOW_FROM).param("toDate", WINDOW_TO)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus", hasSize(3)))
                .andExpect(jsonPath("$.byStatus[?(@.market == 'SG' && @.status == 'SUBMITTED')].claimCount").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.byStatus[?(@.market == 'SG' && @.status == 'SUBMITTED')].totalAmount").value(org.hamcrest.Matchers.contains(500.00)))
                .andExpect(jsonPath("$.byStaff", hasSize(2)))
                .andExpect(jsonPath("$.byType", hasSize(3)))
                .andExpect(jsonPath("$.byType[?(@.claimType == 'HOME')].claimCount").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.byType[?(@.claimType == 'AUTO' && @.market == 'JP')].currency").value(org.hamcrest.Matchers.contains("JPY")));
    }

    @Test
    void aClaimTypeNarrowsStatusAndStaffTotalsButTheTypeBreakdownKeepsEveryType() throws Exception {
        seed();

        mockMvc.perform(get("/api/internal/claims/statistics").param("claimType", "HOME")
                        .param("fromDate", WINDOW_FROM).param("toDate", WINDOW_TO)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus", hasSize(1)))
                .andExpect(jsonPath("$.byStatus[0].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.byStaff", hasSize(1)))
                .andExpect(jsonPath("$.byStaff[0].staffId").value(91))
                .andExpect(jsonPath("$.byType[*].claimType").value(org.hamcrest.Matchers.hasItems("AUTO", "HOME")));
    }

    @Test
    void aNarrowerWindowDropsTheClaimsOutsideIt() throws Exception {
        seed();

        mockMvc.perform(get("/api/internal/claims/statistics").param("fromDate", "2001-03-06").param("toDate", "2001-03-14")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus", hasSize(2)))
                .andExpect(jsonPath("$.byStatus[*].status").value(org.hamcrest.Matchers.containsInAnyOrder("UNDER_REVIEW", "APPROVED")));
    }

    @Test
    void badInputIsAClientError() throws Exception {
        mockMvc.perform(get("/api/internal/claims/statistics").param("claimType", "BOAT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimType").value(containsString("allowed values")));
        mockMvc.perform(get("/api/internal/claims/statistics").param("fromDate", "31-03-2001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/internal/claims/statistics").param("fromDate", WINDOW_TO).param("toDate", WINDOW_FROM)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
        mockMvc.perform(get("/api/internal/claims/trend").param("market", "XX")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/internal/claims/trend").param("fromDate", WINDOW_TO).param("toDate", WINDOW_FROM)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isBadRequest());
    }

    // ---- trend ----------------------------------------------------------------------------------------------

    @Test
    void theTrendPutsEachClaimInItsWeekAndFillsTheQuietOnes() throws Exception {
        seed();

        mockMvc.perform(get("/api/internal/claims/trend").param("fromDate", WINDOW_FROM).param("toDate", WINDOW_TO)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("WEEK"))
                // Weeks of Mon 26 Feb, 5, 12, 19, 26 Mar: the window starts on a Thursday, so the first week begins before it.
                .andExpect(jsonPath("$.periods", hasSize(5)))
                .andExpect(jsonPath("$.periods[0].start").value("2001-02-26"))
                .andExpect(jsonPath("$.periods[1].start").value("2001-03-05"))
                .andExpect(jsonPath("$.periods[1].claimsByStatus.SUBMITTED").value(1))
                .andExpect(jsonPath("$.periods[1].claimsByStatus.UNDER_REVIEW").value(1))
                .andExpect(jsonPath("$.periods[2].start").value("2001-03-12"))
                .andExpect(jsonPath("$.periods[2].claimsByStatus.APPROVED").value(1))
                .andExpect(jsonPath("$.periods[3].claimsByStatus.SUBMITTED").value(0))
                .andExpect(jsonPath("$.periods[4].start").value("2001-03-26"))
                .andExpect(jsonPath("$.periods[4].claimsByStatus.SUBMITTED").value(1));
    }

    @Test
    void theTrendFollowsMarketAndType() throws Exception {
        seed();

        mockMvc.perform(get("/api/internal/claims/trend").param("market", "SG").param("claimType", "AUTO")
                        .param("fromDate", WINDOW_FROM).param("toDate", WINDOW_TO)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods[*].claimsByStatus.SUBMITTED").value(org.hamcrest.Matchers.contains(0, 1, 0, 0, 1)))
                .andExpect(jsonPath("$.periods[*].claimsByStatus.UNDER_REVIEW").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(0))))
                .andExpect(jsonPath("$.periods[*].claimsByStatus.APPROVED").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(0))));
    }

    @Test
    void aWindowWithNoClaimsIsAnEmptyTrendNotAnError() throws Exception {
        mockMvc.perform(get("/api/internal/claims/trend").param("fromDate", "1999-01-01").param("toDate", "1999-01-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods", hasSize(0)))
                .andExpect(jsonPath("$.granularity").value("WEEK"));
    }

    // ---- who may ask ----------------------------------------------------------------------------------------

    @Test
    void claimantsAndAnonymousCallersAreTurnedAway() throws Exception {
        for (String path : new String[]{"/api/internal/claims/statistics", "/api/internal/claims/trend"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(Role.CLAIMANT))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private String bearer(Role role) {
        return "Bearer " + TestJwt.token(jwtSecret, 4242L, role);
    }

    private static Claim claim(String number, String amount, Market market, ClaimType type, String createdAt, Long staffId,
                               ClaimStatus status) {
        Instant at = Instant.parse(createdAt);
        Claim claim = new Claim(number, "POL-STATS-API", 1L, "Claimant", "c@example.com", type, market,
                LocalDate.of(2001, 3, 1), null, "statistics api test", new BigDecimal(amount), at);
        if (staffId != null) {
            claim.assignTo(staffId, at);
            if (status != ClaimStatus.UNDER_REVIEW) {
                claim.transitionTo(status, at);
            }
        }
        return claim;
    }
}
