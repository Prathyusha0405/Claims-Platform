package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.security.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The notification endpoints driven by real claim activity: real JWTs, real filter chain, real database. The
 * notifications under test are raised by the claim lifecycle itself, exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void aClaimantFollowsTheirClaimThroughTheInboxAndClearsTheBadge() throws Exception {
        long jane = randomUserId();
        long officer = randomUserId();
        JsonNode claim = submitClaim(jane);
        String claimNumber = claim.get("claimNumber").asText();

        // Submitting raised one unread notification, which links back to the claim.
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].claimId").value(claim.get("id").asLong()))
                .andExpect(jsonPath("$.content[0].claimNumber").value(claimNumber))
                .andExpect(jsonPath("$.content[0].eventType").value("CLAIM_CREATED"))
                .andExpect(jsonPath("$.content[0].message").value("Your claim " + claimNumber + " has been submitted successfully."))
                .andExpect(jsonPath("$.content[0].read").value(false));
        assertThat(unreadCount(jane, Role.CLAIMANT)).isEqualTo(1);

        // An officer takes it: a second notification, listed first.
        assign(officer, claim.get("id").asLong());
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].eventType").value("CLAIM_ASSIGNED"))
                .andExpect(jsonPath("$.content[1].eventType").value("CLAIM_CREATED"));
        mockMvc.perform(get("/api/notifications").param("sort", "createdAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.content[0].eventType").value("CLAIM_CREATED"))
                .andExpect(jsonPath("$.content[1].eventType").value("CLAIM_ASSIGNED"));
        assertThat(unreadCount(jane, Role.CLAIMANT)).isEqualTo(2);

        // Reading the newest one: it comes back read, the badge drops, and the unread filter hides it.
        long assignedId = objectMapper.readTree(listJson(jane, Role.CLAIMANT)).get("content").get(0).get("id").asLong();
        markRead(jane, assignedId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignedId))
                .andExpect(jsonPath("$.read").value(true));
        assertThat(unreadCount(jane, Role.CLAIMANT)).isEqualTo(1);
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("CLAIM_CREATED"));

        // Marking it again changes nothing and is not an error.
        markRead(jane, assignedId).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        assertThat(unreadCount(jane, Role.CLAIMANT)).isEqualTo(1);

        // "Mark all": reports what it cleared; a second run has nothing left to do.
        readAll(jane, Role.CLAIMANT).andExpect(status().isOk()).andExpect(jsonPath("$.markedRead").value(1));
        readAll(jane, Role.CLAIMANT).andExpect(jsonPath("$.markedRead").value(0));
        assertThat(unreadCount(jane, Role.CLAIMANT)).isZero();
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void nobodyCanSeeOrChangeSomeoneElsesNotifications() throws Exception {
        long jane = randomUserId();
        long stranger = randomUserId();
        submitClaim(jane);
        long janesNotification = objectMapper.readTree(listJson(jane, Role.CLAIMANT)).get("content").get(0).get("id").asLong();

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(stranger, Role.CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(unreadCount(stranger, Role.CLAIMANT)).isZero();

        // Indistinguishable from "does not exist", so ids cannot be probed.
        markRead(stranger, janesNotification).andExpect(status().isNotFound());
        readAll(stranger, Role.CLAIMANT).andExpect(jsonPath("$.markedRead").value(0));

        assertThat(unreadCount(jane, Role.CLAIMANT)).isEqualTo(1);
        markRead(jane, 987_654_321L).andExpect(status().isNotFound());
    }

    @Test
    void theOfficerIsToldWhenTheClaimantAnswersAnInformationRequest() throws Exception {
        long jane = randomUserId();
        long officer = randomUserId();
        long admin = randomUserId();
        JsonNode claim = submitClaim(jane);
        long claimId = claim.get("id").asLong();

        assign(officer, claimId);
        officerAction(officer, claimId, "request-info", "{\"message\":\"Please send the invoice\"}").andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/" + claimId + "/additional-info")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Invoice attached\"}"))
                .andExpect(status().isOk());

        // The officer had nothing before the claimant replied; staff use the same endpoints as claimants.
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(officer, Role.OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("ADDITIONAL_INFO_RECEIVED"))
                .andExpect(jsonPath("$.content[0].claimNumber").value(claim.get("claimNumber").asText()));
        assertThat(unreadCount(officer, Role.OFFICER)).isEqualTo(1);

        // The claimant's story: submitted, assigned, info requested, info received.
        mockMvc.perform(get("/api/notifications").param("sort", "createdAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(jsonPath("$.content[*].eventType").value(org.hamcrest.Matchers.contains(
                        "CLAIM_CREATED", "CLAIM_ASSIGNED", "ADDITIONAL_INFO_REQUESTED", "ADDITIONAL_INFO_RECEIVED")));

        // Admins are never notified, but they are not shut out of the endpoints either.
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(admin, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void badPagingInputIsRejectedOrClamped() throws Exception {
        long jane = randomUserId();

        mockMvc.perform(get("/api/notifications").param("sort", "message").header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field 'message'; allowed fields: [createdAt]"));
        mockMvc.perform(get("/api/notifications").param("size", "100000").header(HttpHeaders.AUTHORIZATION, bearer(jane, Role.CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private JsonNode submitClaim(long claimantId) throws Exception {
        String body = mockMvc.perform(post("/api/claims").header(HttpHeaders.AUTHORIZATION, bearer(claimantId, Role.CLAIMANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNumber":"POL-NOTIFY-1","claimType":"HOME","incidentDate":"2026-10-15",
                                 "description":"Burst pipe in the kitchen","claimAmount":1800.00}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assign(long officerId, long claimId) throws Exception {
        officerAction(officerId, claimId, "assign", null).andExpect(status().isOk());
    }

    private ResultActions officerAction(long officerId, long claimId, String action, String body) throws Exception {
        var request = post("/api/internal/claims/" + claimId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(officerId, Role.OFFICER));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String listJson(long userId, Role role) throws Exception {
        return mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(userId, role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long unreadCount(long userId, Role role) throws Exception {
        String body = mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(userId, role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("unreadCount").asLong();
    }

    private ResultActions markRead(long userId, long notificationId) throws Exception {
        return mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.CLAIMANT)));
    }

    private ResultActions readAll(long userId, Role role) throws Exception {
        return mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(userId, role)));
    }

    private String bearer(long userId, Role role) {
        return "Bearer " + TestJwt.token(jwtSecret, userId, role);
    }

    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }
}
