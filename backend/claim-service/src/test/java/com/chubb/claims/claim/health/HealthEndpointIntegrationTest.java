package com.chubb.claims.claim.health;

import com.chubb.claims.claim.logging.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What an orchestrator, a load balancer or the dev script can rely on: public health probes, nothing else of Actuator. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theHealthAndProbeEndpointsAreAnsweredWithoutATokenAndReportUp() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Test
    void callersWithoutATokenSeeTheStatusOnlyNeverTheComponents() throws Exception {
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
        mockMvc.perform(get("/actuator/health")).andExpect(header().exists(RequestIdFilter.HEADER));
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void aCallersRequestIdIsEchoedAndAHostileOneIsReplaced() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "trace-42.a_B"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-42.a_B"));

        String replaced = mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "two words"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(replaced)).isNotNull();
    }
}
