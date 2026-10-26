package com.chubb.claims.assessment.health;

import com.chubb.claims.assessment.logging.RequestIdFilter;
import com.chubb.claims.assessment.support.AbstractStubbedClaimServiceTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an orchestrator, a load balancer or the dev script can rely on: public health probes, nothing else of Actuator, and a
 * readiness that follows claim-service while liveness does not.
 */
class HealthEndpointIntegrationTest extends AbstractStubbedClaimServiceTest {

    private static void claimServiceIsUp() {
        CLAIM_SERVICE.on("GET", "/actuator/health", 200, "{\"status\":\"UP\"}");
    }

    @Test
    void theHealthAndProbeEndpointsAreAnsweredWithoutATokenAndReportUpWhileClaimServiceIsUp() throws Exception {
        claimServiceIsUp();

        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Test
    void theCheckOfClaimServiceCarriesNoCredentials() throws Exception {
        claimServiceIsUp();

        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        assertThat(CLAIM_SERVICE.lastRequest().path()).isEqualTo("/actuator/health");
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isNull();
    }

    @Test
    void readinessAndOverallHealthGoDownWithClaimServiceButLivenessDoesNot() throws Exception {
        CLAIM_SERVICE.on("GET", "/actuator/health", 503, "{\"status\":\"DOWN\"}");

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anUnexpectedAnswerFromClaimServiceCountsAsDown() throws Exception {
        // Not stubbed: the stub answers 404, as a claim-service without a health endpoint would.
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void callersWithoutATokenSeeTheStatusOnlyNeverTheComponents() throws Exception {
        claimServiceIsUp();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void nothingElseOfActuatorIsReachable() throws Exception {
        for (String path : List.of("/actuator", "/actuator/env", "/actuator/beans", "/actuator/metrics", "/actuator/heapdump")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void everyResponseCarriesARequestIdIncludingARefusal() throws Exception {
        claimServiceIsUp();

        mockMvc.perform(get("/actuator/health")).andExpect(header().exists(RequestIdFilter.HEADER));
        mockMvc.perform(get("/api/staff/claims/unassigned"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void aCallersRequestIdIsEchoedAndAHostileOneIsReplaced() throws Exception {
        claimServiceIsUp();

        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "trace-42.a_B"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-42.a_B"));

        String replaced = mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "two words"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(replaced)).isNotNull();
    }

    @Test
    void theRequestIdOfAStaffActionTravelsToClaimService() throws Exception {
        long staffMember = randomId();
        long claim = randomId();
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 200,
                com.chubb.claims.assessment.support.ClaimJson.claim(claim, "UNDER_REVIEW", staffMember));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/staff/claims/" + claim + "/assign")
                        .header("Authorization", bearer(staffMember, com.chubb.claims.assessment.security.Role.CLAIMS_STAFF))
                        .header(RequestIdFilter.HEADER, "trace-assign-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-assign-1"));

        assertThat(CLAIM_SERVICE.lastRequest().header("x-request-id")).isEqualTo("trace-assign-1");
    }
}
