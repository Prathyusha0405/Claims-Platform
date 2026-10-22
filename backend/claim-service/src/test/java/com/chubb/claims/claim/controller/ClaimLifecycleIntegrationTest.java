package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.entity.ClaimEventType;
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

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The complete claim lifecycle across both halves of claim-service: the claimant's endpoints and the officer actions
 * that assessment-service drives. Real JWTs, real filter chain, real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void submittedAssignedInfoRequestedAnsweredApproved() throws Exception {
        long jane = randomUserId();
        long officer = randomUserId();
        long otherOfficer = randomUserId();
        String claimantToken = token(jane, Role.CLAIMANT);

        // Jane submits; it sits in the work queue as SUBMITTED / unassigned.
        JsonNode created = json(mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, claimantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"policyNumber":"POL-%s","claimType":"HOME","incidentDate":"2026-10-15",
                         "description":"Burst pipe in the kitchen","claimAmount":1800.00}""".formatted(UUID.randomUUID().toString().substring(0, 8)))));
        long claimId = created.get("id").asLong();
        String claimNumber = created.get("claimNumber").asText();
        mockMvc.perform(get("/api/claims/search").param("claimNumber", claimNumber).param("status", "SUBMITTED")
                        .header(HttpHeaders.AUTHORIZATION, token(officer, Role.OFFICER)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignedOfficerId").doesNotExist());

        // Nobody can decide a claim before it is assigned.
        action(officer, claimId, "approve", "{}").andExpect(status().isForbidden());

        // An officer takes it.
        action(officer, claimId, "assign", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerId").value(officer));
        action(otherOfficer, claimId, "assign", null).andExpect(status().isConflict());

        // Only the assigned officer may act on it.
        action(otherOfficer, claimId, "approve", "{}").andExpect(status().isForbidden());
        action(otherOfficer, claimId, "reject", "{\"reason\":\"not mine\"}").andExpect(status().isForbidden());

        // The officer asks for more information; the claimant sees it and answers.
        action(officer, claimId, "request-info", "{\"message\":\"Please send the plumber's invoice\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MORE_INFO_REQUIRED"));
        action(officer, claimId, "approve", "{}").andExpect(status().isConflict());
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, claimantToken))
                .andExpect(jsonPath("$.status").value("MORE_INFO_REQUIRED"));

        mockMvc.perform(post("/api/claims/" + claimId + "/additional-info").header(HttpHeaders.AUTHORIZATION, claimantToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Invoice INV-77 attached\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // The officer approves (comment optional).
        action(officer, claimId, "approve", "{\"comment\":\"Invoice matches the damage\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // A decided claim cannot be decided again.
        action(officer, claimId, "reject", "{\"reason\":\"changed my mind\"}").andExpect(status().isConflict());
        action(officer, claimId, "request-info", "{\"message\":\"more\"}").andExpect(status().isConflict());

        // The audit trail tells the whole story, in order, and the claimant saw the decisions.
        mockMvc.perform(get("/api/claims/" + claimNumber).header(HttpHeaders.AUTHORIZATION, claimantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history[*].eventType").value(org.hamcrest.Matchers.contains(
                        "CLAIM_CREATED", "CLAIM_ASSIGNED", "ADDITIONAL_INFO_REQUESTED", "ADDITIONAL_INFO_RECEIVED", "CLAIM_APPROVED")))
                .andExpect(jsonPath("$.history[2].details").value("Please send the plumber's invoice"))
                .andExpect(jsonPath("$.history[2].performedBy").value(officer))
                .andExpect(jsonPath("$.history[2].performedByRole").value("OFFICER"))
                .andExpect(jsonPath("$.history[4].details").value("Invoice matches the damage"))
                .andExpect(jsonPath("$.history[4].fromStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.history[4].toStatus").value("APPROVED"));

        assertThat(notificationRepository.findByRecipientId(jane, PageRequest.of(0, 20)).getContent())
                .extracting(notification -> notification.getEventType())
                .containsExactlyInAnyOrder(ClaimEventType.CLAIM_CREATED, ClaimEventType.CLAIM_ASSIGNED,
                        ClaimEventType.ADDITIONAL_INFO_REQUESTED, ClaimEventType.ADDITIONAL_INFO_RECEIVED,
                        ClaimEventType.CLAIM_APPROVED);
        // The officer was told when the claimant answered.
        assertThat(notificationRepository.findByRecipientId(officer, PageRequest.of(0, 20)).getContent())
                .extracting(notification -> notification.getEventType())
                .containsExactly(ClaimEventType.ADDITIONAL_INFO_RECEIVED);

        // The internal read shows the final state, and the statistics include it.
        mockMvc.perform(get("/api/internal/claims/" + claimId).header(HttpHeaders.AUTHORIZATION, token(officer, Role.OFFICER)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.assignedOfficerId").value(officer));
        mockMvc.perform(get("/api/internal/claims/statistics").header(HttpHeaders.AUTHORIZATION, token(officer, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus[*].status").value(hasItem("APPROVED")))
                .andExpect(jsonPath("$.byOfficer[?(@.officerId == %d)].status".formatted(officer)).value(hasItem("APPROVED")));
    }

    @Test
    void aRejectedClaimRecordsTheReasonForTheClaimant() throws Exception {
        long jane = randomUserId();
        long officer = randomUserId();
        String claimantToken = token(jane, Role.CLAIMANT);
        JsonNode created = json(mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, claimantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"policyNumber":"POL-REJ-1","claimType":"AUTO","incidentDate":"2026-10-15",
                         "description":"Scratch","claimAmount":90.00}""")));
        long claimId = created.get("id").asLong();

        action(officer, claimId, "assign", null).andExpect(status().isOk());
        action(officer, claimId, "reject", "{\"reason\":\"Below the policy excess\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/claims/" + created.get("claimNumber").asText()).header(HttpHeaders.AUTHORIZATION, claimantToken))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.history[2].eventType").value("CLAIM_REJECTED"))
                .andExpect(jsonPath("$.history[2].details").value("Below the policy excess"));
    }

    @Test
    void actionsOnAnUnknownClaimAre404() throws Exception {
        action(randomUserId(), 987_654_321L, "assign", null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/internal/claims/987654321").header(HttpHeaders.AUTHORIZATION, token(randomUserId(), Role.OFFICER)))
                .andExpect(status().isNotFound());
    }

    private ResultActions action(long officerId, long claimId, String action, String body) throws Exception {
        var request = post("/api/internal/claims/" + claimId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, token(officerId, Role.OFFICER));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String token(long userId, Role role) {
        return "Bearer " + TestJwt.token(jwtSecret, userId, role);
    }

    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }
}
