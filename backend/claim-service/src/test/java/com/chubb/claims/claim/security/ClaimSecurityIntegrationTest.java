package com.chubb.claims.claim.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, role rules and public documentation endpoints against the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimSecurityIntegrationTest {

    private static final String CLAIM_BODY = """
            {"policyNumber":"POL-SEC-1","claimType":"AUTO","market":"SG","incidentDate":"2026-10-15",
             "description":"security test","claimAmount":10.00}""";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void everyClaimEndpointRejectsAnonymousCallersWithJson401() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY),
                get("/api/claims/my-claims"),
                get("/api/claims/search"),
                get("/api/claims/CLM-2026-000001"),
                post("/api/claims/1/additional-info").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}"),
                get("/api/notifications"),
                get("/api/notifications/unread-count"),
                post("/api/notifications/1/read"),
                post("/api/notifications/read-all")}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void forgedExpiredForeignAndMalformedTokensAreRejected() throws Exception {
        String valid = TestJwt.token(jwtSecret, 1L, Role.CLAIMANT);
        String[] parts = valid.split("\\.");
        String adminPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"MANAGER\"}".getBytes());

        String[] badTokens = {
                parts[0] + "." + adminPayload + "." + parts[2],
                TestJwt.token(jwtSecret, 1L, "a@b.com", "A", Role.CLAIMANT, -1_000L),
                TestJwt.token("another-secret-key-that-is-long-enough-1234", 1L, Role.MANAGER),
                "not-a-jwt"};

        for (String bad : badTokens) {
            mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotCreateClaimsListMyClaimsOrAnswerInformationRequests() throws Exception {
        for (Role staff : new Role[]{Role.CLAIMS_STAFF, Role.MANAGER}) {
            String bearer = "Bearer " + TestJwt.token(jwtSecret, 2L, staff);

            mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/claims/1/additional-info").header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void everyRoleMayTrackAndSearch() throws Exception {
        for (Role role : Role.values()) {
            String bearer = "Bearer " + TestJwt.token(jwtSecret, 3L, role);

            mockMvc.perform(get("/api/claims/search").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
            // Unknown claim: authorized (so 404, not 403) for every role.
            mockMvc.perform(get("/api/claims/CLM-1900-000001").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void everyRoleHasItsOwnNotifications() throws Exception {
        for (Role role : Role.values()) {
            String bearer = "Bearer " + TestJwt.token(jwtSecret, 3L, role);

            mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
            mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
            // Unknown notification: authorized (so 404, not 403) for every role.
            mockMvc.perform(post("/api/notifications/987654321/read").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void internalStaffMemberEndpointsFollowTheRoleRules() throws Exception {
        String claimant = "Bearer " + TestJwt.token(jwtSecret, 4L, Role.CLAIMANT);
        String staffMember = "Bearer " + TestJwt.token(jwtSecret, 5L, Role.CLAIMS_STAFF);
        String admin = "Bearer " + TestJwt.token(jwtSecret, 6L, Role.MANAGER);

        // anonymous
        mockMvc.perform(get("/api/internal/claims/statistics")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/internal/claims/1/assign")).andExpect(status().isUnauthorized());

        // claimants: nothing
        mockMvc.perform(get("/api/internal/claims/statistics").header(HttpHeaders.AUTHORIZATION, claimant))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/internal/claims/1/assign").header(HttpHeaders.AUTHORIZATION, claimant))
                .andExpect(status().isForbidden());

        // managers: read-only
        mockMvc.perform(get("/api/internal/claims/statistics").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/internal/claims/1/assign").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());

        // claims staff: read and act (claim 1 may not exist -> 404 proves it was authorized)
        mockMvc.perform(get("/api/internal/claims/statistics").header(HttpHeaders.AUTHORIZATION, staffMember))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/internal/claims/987654321/assign").header(HttpHeaders.AUTHORIZATION, staffMember))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsPublicAtTheDocumentedAddress() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiDocumentDescribesTheClaimAndNotificationEndpointsAndBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Claim Service API"))
                .andExpect(jsonPath("$.paths['/api/claims'].post").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{claimNumber}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims/my-claims'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims/search'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{id}/additional-info'].post").exists())
                .andExpect(jsonPath("$.paths['/api/notifications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/unread-count'].get").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].post").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/read-all'].post").exists())
                .andExpect(jsonPath("$.paths['/api/notifications'].get.parameters[*].name").value(hasItems(
                        "unreadOnly", "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].post.responses['404']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/claims/search'].get.parameters[*].name").value(hasItems(
                        "claimNumber", "policyNumber", "status", "fromDate", "toDate", "assignedStaffId",
                        "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/claims'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{id}/additional-info'].post.responses['409']").exists());
    }

    @Test
    void errorResponsesAreDocumentedWithTheErrorSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/claims/{claimNumber}'].get.responses['404'].content['*/*'].schema.$ref")
                        .value(startsWith("#/components/schemas/ErrorResponse")));
    }
}
