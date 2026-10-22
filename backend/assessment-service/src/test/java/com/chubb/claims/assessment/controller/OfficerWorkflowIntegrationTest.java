package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.ClaimAssessment;
import com.chubb.claims.assessment.entity.ClaimNote;
import com.chubb.claims.assessment.entity.NoteType;
import com.chubb.claims.assessment.repository.ClaimAssessmentRepository;
import com.chubb.claims.assessment.repository.ClaimNoteRepository;
import com.chubb.claims.assessment.security.Role;
import com.chubb.claims.assessment.support.AbstractStubbedClaimServiceTest;
import com.chubb.claims.assessment.support.ClaimJson;
import com.chubb.claims.assessment.support.StubClaimServer.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An officer's journey through assessment-service: real JWT, real filter chain, real database, and a real HTTP call
 * to a stub standing in for claim-service. The stub proves what is forwarded; the repositories prove what is stored.
 */
class OfficerWorkflowIntegrationTest extends AbstractStubbedClaimServiceTest {

    @Autowired
    private ClaimAssessmentRepository assessmentRepository;

    @Autowired
    private ClaimNoteRepository noteRepository;

    @Test
    void anOfficerTakesAClaimAsksForInformationAndApprovesIt() throws Exception {
        long officer = randomId();
        long claim = randomId();
        String officerToken = token(officer, Role.OFFICER);

        // The work queue is claim-service's search for SUBMITTED claims, called with the officer's own token.
        CLAIM_SERVICE.on("GET", "/api/claims/search", 200, ClaimJson.page(ClaimJson.claim(claim, "SUBMITTED", null)));
        mockMvc.perform(get("/api/officer/claims/unassigned").param("size", "5").param("sort", "claimAmount,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(claim))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.content[0].claimAmount").value(2500.00));
        RecordedRequest queueRequest = CLAIM_SERVICE.lastRequest();
        assertThat(queueRequest.query()).isEqualTo("status=SUBMITTED&page=0&size=5&sort=claimAmount,desc");
        assertThat(queueRequest.header("authorization")).isEqualTo("Bearer " + officerToken);

        // Assign: claim-service moves it; this service records the officer's assessment.
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 200, ClaimJson.claim(claim, "UNDER_REVIEW", officer));
        action(officer, claim, "assign", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claim))
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-%06d".formatted(claim)))
                .andExpect(jsonPath("$.claimStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.officerId").value(officer))
                .andExpect(jsonPath("$.officerName").value("User " + officer))
                .andExpect(jsonPath("$.decision").doesNotExist());
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isEqualTo("Bearer " + officerToken);

        ClaimAssessment assigned = assessmentRepository.findByClaimId(claim).orElseThrow();
        assertThat(assigned.getOfficerId()).isEqualTo(officer);
        assertThat(assigned.getAssignedAt()).isNotNull();
        assertThat(assigned.getDecision()).isNull();

        // Ask the claimant for more information: forwarded, noted, no decision yet.
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/request-info", 200,
                ClaimJson.claim(claim, "MORE_INFO_REQUIRED", officer));
        action(officer, claim, "request-info", "{\"message\":\"  Please send the repair estimate  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("MORE_INFO_REQUIRED"))
                .andExpect(jsonPath("$.decision").doesNotExist());
        assertThat(CLAIM_SERVICE.lastRequest().body()).isEqualTo("{\"message\":\"Please send the repair estimate\"}");

        // Approve with a comment.
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/approve", 200, ClaimJson.claim(claim, "APPROVED", officer));
        action(officer, claim, "approve", "{\"comment\":\"Estimate matches the damage\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value("Estimate matches the damage"))
                .andExpect(jsonPath("$.decidedAt").exists());
        assertThat(CLAIM_SERVICE.lastRequest().body()).isEqualTo("{\"comment\":\"Estimate matches the damage\"}");

        ClaimAssessment decided = assessmentRepository.findByClaimId(claim).orElseThrow();
        assertThat(decided.getId()).isEqualTo(assigned.getId());      // one row per claim, updated in place
        assertThat(decided.getDecision()).isEqualTo(AssessmentDecision.APPROVED);
        assertThat(decided.getDecidedAt()).isNotNull();
        assertThat(decided.getVersion()).isGreaterThan(0);

        List<ClaimNote> trail = noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim);
        assertThat(trail).extracting(ClaimNote::getNoteType).containsExactly(NoteType.INFO_REQUEST, NoteType.APPROVAL);
        assertThat(trail).extracting(ClaimNote::getContent)
                .containsExactly("Please send the repair estimate", "Estimate matches the damage");
        assertThat(trail).allSatisfy(note -> assertThat(note.getOfficerId()).isEqualTo(officer));
    }

    @Test
    void approvingWithoutABodyIsAccepted() throws Exception {
        long officer = randomId();
        long claim = randomId();
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/approve", 200, ClaimJson.claim(claim, "APPROVED", officer));

        action(officer, claim, "approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").doesNotExist());

        // The local assessment was recreated even though assignment was never recorded here.
        assertThat(assessmentRepository.findByClaimId(claim)).isPresent();
        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim)).isEmpty();
    }

    @Test
    void rejectingRecordsTheReasonAndItIsRequired() throws Exception {
        long officer = randomId();
        long claim = randomId();
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/reject", 200, ClaimJson.claim(claim, "REJECTED", officer));

        // No reason: rejected here, claim-service is never called.
        action(officer, claim, "reject", "{\"reason\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        assertThat(CLAIM_SERVICE.requests()).isEmpty();

        action(officer, claim, "reject", "{\"reason\":\"Policy had lapsed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"))
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Policy had lapsed"));

        assertThat(assessmentRepository.findByClaimId(claim).orElseThrow().getDecision()).isEqualTo(AssessmentDecision.REJECTED);
        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim)).singleElement().satisfies(note -> {
            assertThat(note.getNoteType()).isEqualTo(NoteType.REJECTION);
            assertThat(note.getContent()).isEqualTo("Policy had lapsed");
        });
    }

    @Test
    void anyOfficerCanNoteAnExistingClaimButNotAMissingOne() throws Exception {
        long officer = randomId();
        long claim = randomId();
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + claim, 200, ClaimJson.claim(claim, "SUBMITTED", null));

        action(officer, claim, "notes", "{\"content\":\"  Called the workshop  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.claimId").value(claim))
                .andExpect(jsonPath("$.officerId").value(officer))
                .andExpect(jsonPath("$.noteType").value("GENERAL"))
                .andExpect(jsonPath("$.content").value("Called the workshop"));
        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim)).hasSize(1);

        long missing = randomId();
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + missing, 404, ClaimJson.error(404, "Claim '" + missing + "' was not found"));
        action(officer, missing, "notes", "{\"content\":\"ghost\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Claim '" + missing + "' was not found"));
        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(missing)).isEmpty();

        action(officer, claim, "notes", "{\"content\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void theReviewTrailIsReadOldestFirstByAnyOfficerOnceTheClaimIsConfirmed() throws Exception {
        long officer = randomId();
        long colleague = randomId();
        long claim = randomId();
        String colleagueBearer = bearer(colleague, Role.OFFICER);
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + claim, 200, ClaimJson.claim(claim, "UNDER_REVIEW", officer));
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/request-info", 200,
                ClaimJson.claim(claim, "MORE_INFO_REQUIRED", officer));
        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/approve", 200, ClaimJson.claim(claim, "APPROVED", officer));

        // Nothing written yet: an empty trail, not an error. The claim was confirmed with the reader's own token.
        mockMvc.perform(get("/api/officer/claims/" + claim + "/notes").header(HttpHeaders.AUTHORIZATION, colleagueBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(CLAIM_SERVICE.lastRequest().method()).isEqualTo("GET");
        assertThat(CLAIM_SERVICE.lastRequest().path()).isEqualTo("/api/internal/claims/" + claim);
        assertThat(CLAIM_SERVICE.lastRequest().header("authorization")).isEqualTo(colleagueBearer);

        // The trail grows from three different actions, and a note on some other claim never appears in it.
        long other = randomId();
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + other, 200, ClaimJson.claim(other, "SUBMITTED", null));
        action(officer, claim, "request-info", "{\"message\":\"Please send the repair estimate\"}").andExpect(status().isOk());
        action(colleague, claim, "notes", "{\"content\":\"Estimate looks high\"}").andExpect(status().isCreated());
        action(officer, other, "notes", "{\"content\":\"Unrelated claim\"}").andExpect(status().isCreated());
        action(officer, claim, "approve", "{\"comment\":\"Estimate checked\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/officer/claims/" + claim + "/notes").header(HttpHeaders.AUTHORIZATION, colleagueBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].noteType").value(contains("INFO_REQUEST", "GENERAL", "APPROVAL")))
                .andExpect(jsonPath("$[*].content").value(contains(
                        "Please send the repair estimate", "Estimate looks high", "Estimate checked")))
                .andExpect(jsonPath("$[0].claimId").value(claim))
                .andExpect(jsonPath("$[0].officerId").value(officer))
                .andExpect(jsonPath("$[0].officerName").value("User " + officer))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].officerId").value(colleague))
                .andExpect(jsonPath("$[2].claimId").value(claim));
    }

    @Test
    void theNotesOfAMissingClaimAreA404AndAClaimServiceOutageIsA503() throws Exception {
        long officer = randomId();
        long missing = randomId();
        long claim = randomId();
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + missing, 404, ClaimJson.error(404, "Claim '" + missing + "' was not found"));
        CLAIM_SERVICE.on("GET", "/api/internal/claims/" + claim, 500, ClaimJson.error(500, "boom"));

        mockMvc.perform(get("/api/officer/claims/" + missing + "/notes").header(HttpHeaders.AUTHORIZATION, bearer(officer, Role.OFFICER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Claim '" + missing + "' was not found"));
        mockMvc.perform(get("/api/officer/claims/" + claim + "/notes").header(HttpHeaders.AUTHORIZATION, bearer(officer, Role.OFFICER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Claim service failed with HTTP 500"));
    }

    @Test
    void claimServiceRefusalsReachTheOfficerAndNothingIsStored() throws Exception {
        long officer = randomId();
        long claim = randomId();

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 409,
                ClaimJson.error(409, "Only a SUBMITTED claim can be assigned to an officer (current status: UNDER_REVIEW)"));
        action(officer, claim, "assign", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a SUBMITTED claim can be assigned to an officer (current status: UNDER_REVIEW)"));

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/approve", 403, ClaimJson.error(403, "This claim is not assigned to you"));
        action(officer, claim, "approve", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This claim is not assigned to you"));

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/request-info", 409,
                ClaimJson.error(409, "Cannot move a claim from APPROVED to MORE_INFO_REQUIRED"));
        action(officer, claim, "request-info", "{\"message\":\"x\"}").andExpect(status().isConflict());

        assertThat(assessmentRepository.findByClaimId(claim)).isEmpty();
        assertThat(noteRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim)).isEmpty();
    }

    @Test
    void claimServiceTroubleBecomes503Or502() throws Exception {
        long officer = randomId();
        long claim = randomId();

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 500, ClaimJson.error(500, "boom"));
        action(officer, claim, "assign", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Claim service failed with HTTP 500"));

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 401, ClaimJson.error(401, "Authentication is required"));
        action(officer, claim, "assign", null)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Claim service did not accept the caller's credentials"));

        CLAIM_SERVICE.on("POST", "/api/internal/claims/" + claim + "/assign", 200, "{ not json");
        action(officer, claim, "assign", null).andExpect(status().isBadGateway());

        assertThat(assessmentRepository.findByClaimId(claim)).isEmpty();
    }

    private ResultActions action(long officerId, long claimId, String action, String body) throws Exception {
        var request = post("/api/officer/claims/" + claimId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(officerId, Role.OFFICER));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
