package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.security.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole claim lifecycle through the real filter chain, real JWTs, real services and the real Flyway-built
 * database. Tests use random claimant ids and unique policy numbers so they never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void aClaimGoesFromSubmissionThroughMoreInfoRequestedBackToReview() throws Exception {
        long jane = randomUserId();
        long bob = randomUserId();
        long officer = randomUserId();
        String policy = uniquePolicy();

        // 1. Jane submits a claim.
        ResultActions created = createClaim(token(jane, Role.CLAIMANT), policy, "2500.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimantId").value(jane))
                .andExpect(jsonPath("$.claimantName").value("User " + jane))
                .andExpect(jsonPath("$.claimNumber").value(matchesPattern("CLM-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].eventType").value("CLAIM_CREATED"))
                .andExpect(jsonPath("$.history[0].toStatus").value("SUBMITTED"));
        JsonNode claim = json(created);
        String claimNumber = claim.get("claimNumber").asText();
        long claimId = claim.get("id").asLong();
        created.andExpect(header().string("Location", containsString("/api/claims/" + claimNumber)));

        // 2. Tracking: the owner and any officer can open it; another claimant cannot, even by lower-case number.
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, token(jane, Role.CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value(policy));
        mockMvc.perform(get("/api/claims/" + claimNumber.toLowerCase()).header(HttpHeaders.AUTHORIZATION, token(jane, Role.CLAIMANT)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, token(officer, Role.OFFICER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, token(bob, Role.CLAIMANT)))
                .andExpect(status().isNotFound());

        // 3. Listing and search respect ownership.
        mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, token(jane, Role.CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].claimNumber").value(claimNumber));
        mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, token(bob, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy)
                        .header(HttpHeaders.AUTHORIZATION, token(officer, Role.OFFICER)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy)
                        .header(HttpHeaders.AUTHORIZATION, token(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy)
                        .header(HttpHeaders.AUTHORIZATION, token(bob, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // 4. The claim is not awaiting information yet, so answering is a conflict.
        additionalInfo(token(jane, Role.CLAIMANT), claimId, "too early").andExpect(status().isConflict());

        // 5. An officer asks for more information (assessment-service will do this; simulated directly for now).
        Claim stored = claimRepository.findById(claimId).orElseThrow();
        stored.transitionTo(ClaimStatus.UNDER_REVIEW, Instant.now());
        stored.transitionTo(ClaimStatus.MORE_INFO_REQUIRED, Instant.now());
        claimRepository.save(stored);

        // 6. Only the owner can answer.
        additionalInfo(token(bob, Role.CLAIMANT), claimId, "not mine").andExpect(status().isNotFound());
        additionalInfo(token(jane, Role.CLAIMANT), claimId, "Repair estimate attached")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].eventType").value("ADDITIONAL_INFO_RECEIVED"))
                .andExpect(jsonPath("$.history[1].fromStatus").value("MORE_INFO_REQUIRED"))
                .andExpect(jsonPath("$.history[1].toStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.history[1].performedBy").value(jane))
                .andExpect(jsonPath("$.history[1].performedByRole").value("CLAIMANT"))
                .andExpect(jsonPath("$.history[1].details").value("Repair estimate attached"));

        // 7. It cannot be answered twice.
        additionalInfo(token(jane, Role.CLAIMANT), claimId, "again").andExpect(status().isConflict());

        // 8. Jane was notified of both events.
        assertThat(notificationRepository.findByRecipientId(jane, PageRequest.of(0, 10)).getContent())
                .extracting(notification -> notification.getEventType().name())
                .containsExactlyInAnyOrder("CLAIM_CREATED", "ADDITIONAL_INFO_RECEIVED");
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(jane)).isEqualTo(2);
    }

    @Test
    void claimNumbersAreUniqueAndSequential() throws Exception {
        String token = token(randomUserId(), Role.CLAIMANT);

        String first = json(createClaim(token, uniquePolicy(), "10.00")).get("claimNumber").asText();
        String second = json(createClaim(token, uniquePolicy(), "10.00")).get("claimNumber").asText();

        assertThat(first).isNotEqualTo(second);
        assertThat(Long.parseLong(second.substring(second.lastIndexOf('-') + 1)))
                .isGreaterThan(Long.parseLong(first.substring(first.lastIndexOf('-') + 1)));
    }

    @Test
    void myClaimsPagesAndSortsAndRejectsUnsupportedSortFields() throws Exception {
        String token = token(randomUserId(), Role.CLAIMANT);
        createClaim(token, uniquePolicy(), "300.00").andExpect(status().isCreated());
        createClaim(token, uniquePolicy(), "100.00").andExpect(status().isCreated());
        createClaim(token, uniquePolicy(), "200.00").andExpect(status().isCreated());

        mockMvc.perform(get("/api/claims/my-claims").param("size", "2").param("sort", "claimAmount,asc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].claimAmount").value(100.00))
                .andExpect(jsonPath("$.content[1].claimAmount").value(200.00));

        mockMvc.perform(get("/api/claims/my-claims").param("size", "2").param("page", "1").param("sort", "claimAmount,asc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].claimAmount").value(300.00))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/claims/my-claims").param("sort", "claimAmount,desc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.content[0].claimAmount").value(300.00));

        // Newest first by default.
        mockMvc.perform(get("/api/claims/my-claims").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.content[0].claimAmount").value(200.00));

        mockMvc.perform(get("/api/claims/my-claims").param("sort", "claimantEmail,asc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported sort field 'claimantEmail'")));
    }

    @Test
    void searchFiltersByStatusDateRangeAndOfficer() throws Exception {
        long claimant = randomUserId();
        String policy = uniquePolicy();
        long claimId = json(createClaim(token(claimant, Role.CLAIMANT), policy, "50.00")).get("id").asLong();
        String officer = token(randomUserId(), Role.OFFICER);
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy).param("status", "SUBMITTED")
                        .param("fromDate", today).param("toDate", today).header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy).param("status", "APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy).param("toDate", "2020-01-01")
                        .header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy).param("assignedOfficerId", "99")
                        .header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(0));

        Claim assigned = claimRepository.findById(claimId).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(assigned, "assignedOfficerId", 99L);
        claimRepository.save(assigned);
        mockMvc.perform(get("/api/claims/search").param("policyNumber", policy).param("assignedOfficerId", "99")
                        .header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignedOfficerId").value(99));

        mockMvc.perform(get("/api/claims/search").param("fromDate", "2026-12-31").param("toDate", "2026-01-01")
                        .header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
    }

    @Test
    void claimsAreValidatedBeforeTheyAreStored() throws Exception {
        String token = token(randomUserId(), Role.CLAIMANT);
        long before = claimRepository.count();

        mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNumber":"POL-9999","claimType":"AUTO","incidentDate":"2999-01-01",
                                 "description":"from the future","claimAmount":10.00}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("incidentDate cannot be in the future"));
        mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNumber":"POL-9999","claimType":"AUTO","incidentDate":"2026-01-01",
                                 "description":"free money","claimAmount":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimAmount").exists());

        assertThat(claimRepository.count()).isEqualTo(before);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ResultActions createClaim(String bearer, String policyNumber, String amount) throws Exception {
        return mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"policyNumber":"%s","claimType":"AUTO","incidentDate":"2026-10-15","incidentLocation":"Orchard Road",
                         "description":"Rear-ended at a traffic light","claimAmount":%s}""".formatted(policyNumber, amount)));
    }

    private ResultActions additionalInfo(String bearer, long claimId, String message) throws Exception {
        return mockMvc.perform(post("/api/claims/" + claimId + "/additional-info").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message":"%s"}""".formatted(message)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String token(long userId, Role role) {
        return "Bearer " + TestJwt.token(jwtSecret, userId, role);
    }

    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }

    private static String uniquePolicy() {
        return "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
