package com.chubb.claims.assessment.security;

import com.chubb.claims.assessment.support.AbstractStubbedClaimServiceTest;
import com.chubb.claims.assessment.support.ClaimJson;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, role rules and the public documentation endpoints against the real filter chain. */
class AssessmentSecurityIntegrationTest extends AbstractStubbedClaimServiceTest {

    private static final List<String> DASHBOARDS = List.of(
            "/api/dashboard/status-summary", "/api/dashboard/staff-workload", "/api/dashboard/liability-exposure",
            "/api/dashboard/claims-over-time");

    private List<MockHttpServletRequestBuilder> allEndpoints() {
        List<MockHttpServletRequestBuilder> requests = new java.util.ArrayList<>();
        requests.add(get("/api/staff/claims/unassigned"));
        requests.add(get("/api/staff/claims/10/notes"));
        for (String action : List.of("assign", "approve", "reject", "request-info", "notes")) {
            requests.add(post("/api/staff/claims/10/" + action).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"x\",\"message\":\"x\",\"content\":\"x\"}"));
        }
        DASHBOARDS.forEach(dashboard -> requests.add(get(dashboard)));
        return requests;
    }

    @Test
    void everyEndpointRejectsAnonymousCallersWithJson401() throws Exception {
        for (MockHttpServletRequestBuilder request : allEndpoints()) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void forgedExpiredForeignAndMalformedTokensAreRejected() throws Exception {
        String[] parts = token(1L, Role.CLAIMS_STAFF).split("\\.");
        String adminPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\",\"role\":\"MANAGER\"}".getBytes());

        String[] badTokens = {
                parts[0] + "." + adminPayload + "." + parts[2],
                TestJwt.token(jwtSecret, 1L, "a@b.com", "A", Role.MANAGER, -1_000L),
                TestJwt.token("another-secret-key-that-is-long-enough-1234", 1L, Role.MANAGER),
                "not-a-jwt"};

        for (String bad : badTokens) {
            mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/staff/claims/unassigned").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/dashboard/status-summary").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimantsAreForbiddenEverywhere() throws Exception {
        String claimant = bearer(3L, Role.CLAIMANT);

        for (MockHttpServletRequestBuilder request : allEndpoints()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, claimant))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    @Test
    void claimsStaffUseTheStaffApiButNotTheDashboards() throws Exception {
        String staffMember = bearer(4L, Role.CLAIMS_STAFF);
        CLAIM_SERVICE.on("GET", "/api/claims/search", 200, ClaimJson.page());
        CLAIM_SERVICE.on("GET", "/api/internal/claims/10", 200, ClaimJson.claim(10L, "SUBMITTED", null));

        mockMvc.perform(get("/api/staff/claims/unassigned").header(HttpHeaders.AUTHORIZATION, staffMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/staff/claims/10/notes").header(HttpHeaders.AUTHORIZATION, staffMember))
                .andExpect(status().isOk());
        for (String dashboard : DASHBOARDS) {
            mockMvc.perform(get(dashboard).header(HttpHeaders.AUTHORIZATION, staffMember)).andExpect(status().isForbidden());
        }
    }

    @Test
    void managersUseTheDashboardsButNotTheStaffApi() throws Exception {
        String admin = bearer(5L, Role.MANAGER);
        CLAIM_SERVICE.on("GET", "/api/internal/claims/statistics", 200, ClaimJson.statistics());
        CLAIM_SERVICE.on("GET", "/api/internal/claims/trend", 200, ClaimJson.trend());

        for (String dashboard : DASHBOARDS) {
            mockMvc.perform(get(dashboard).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/staff/claims/unassigned").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/claims/10/notes").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/claims/10/assign").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerUiIsPublicAtTheDocumentedAddress() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiDocumentDescribesAllTenEndpointsAndBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Assessment Service API"))
                .andExpect(jsonPath("$.paths['/api/staff/claims/unassigned'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/assign'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/reject'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/request-info'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/notes'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/notes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/notes'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/status-summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/staff-workload'].get").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/liability-exposure'].get").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/claims-over-time'].get").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/status-summary'].get.parameters[*].name")
                        .value(hasItems("market", "claimType", "fromDate", "toDate")))
                .andExpect(jsonPath("$.paths['/api/dashboard/claims-over-time'].get.parameters[*].name")
                        .value(hasItems("market", "claimType", "fromDate", "toDate")))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/staff/claims/unassigned'].get.parameters[*].name")
                        .value(hasItems("page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/notes'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{id}/reject'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/liability-exposure'].get.responses['403']").exists());
    }
}
