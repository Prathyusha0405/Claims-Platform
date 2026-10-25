package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.config.SecurityConfig;
import com.chubb.claims.assessment.dto.AddNoteRequest;
import com.chubb.claims.assessment.dto.ApproveClaimRequest;
import com.chubb.claims.assessment.dto.SettleClaimRequest;
import com.chubb.claims.assessment.dto.AssessmentResponse;
import com.chubb.claims.assessment.dto.ClaimSummaryResponse;
import com.chubb.claims.assessment.dto.NoteResponse;
import com.chubb.claims.assessment.dto.PageResponse;
import com.chubb.claims.assessment.dto.RejectClaimRequest;
import com.chubb.claims.assessment.dto.RequestInfoRequest;
import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.NoteType;
import com.chubb.claims.assessment.exception.ClaimAccessDeniedException;
import com.chubb.claims.assessment.exception.ClaimConflictException;
import com.chubb.claims.assessment.exception.ClaimNotFoundException;
import com.chubb.claims.assessment.exception.ClaimServiceProtocolException;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.exception.InvalidRequestException;
import com.chubb.claims.assessment.security.AuthenticatedUser;
import com.chubb.claims.assessment.security.JwtService;
import com.chubb.claims.assessment.security.Role;
import com.chubb.claims.assessment.service.AssessmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffClaimController.class)
@Import(SecurityConfig.class)
class StaffClaimControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private static final AuthenticatedUser CLAIMS_STAFF = new AuthenticatedUser(7L, "staff@chubb.com", "Demo Claims Staff", Role.CLAIMS_STAFF);
    private static final AuthenticatedUser MANAGER = new AuthenticatedUser(8L, "manager@chubb.com", "Demo Manager", Role.MANAGER);
    private static final AuthenticatedUser CLAIMANT = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    @MockitoBean
    private JwtService jwtService;

    // ---- GET /unassigned --------------------------------------------------------------------------------------

    @Test
    void theWorkQueueDefaultsToOldestFirstTwentyPerPage() throws Exception {
        when(assessmentService.getUnassignedClaims(any(), any(Pageable.class))).thenReturn(queue());

        mockMvc.perform(get("/api/staff/claims/unassigned").with(as(CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimNumber").value("CLM-2026-000010"))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assessmentService).getUnassignedClaims(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.asc("createdAt"));
    }

    @Test
    void theWorkQueuePassesPagingAndSortingThroughAndCapsThePageSize() throws Exception {
        when(assessmentService.getUnassignedClaims(any(), any(Pageable.class))).thenReturn(queue());

        mockMvc.perform(get("/api/staff/claims/unassigned").with(as(CLAIMS_STAFF))
                        .param("page", "3").param("size", "5000").param("sort", "claimAmount,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assessmentService).getUnassignedClaims(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("claimAmount"));
    }

    @Test
    void theWorkQueueCanBeNarrowedToOneMarket() throws Exception {
        when(assessmentService.getUnassignedClaims(any(), any(Pageable.class))).thenReturn(queue());

        mockMvc.perform(get("/api/staff/claims/unassigned").with(as(CLAIMS_STAFF)).param("market", "SG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].market").value("SG"))
                .andExpect(jsonPath("$.content[0].currency").value("SGD"));

        verify(assessmentService).getUnassignedClaims(eq("SG"), any(Pageable.class));
    }

    @Test
    void anUnknownMarketReportedByClaimServiceIs400() throws Exception {
        when(assessmentService.getUnassignedClaims(any(), any(Pageable.class)))
                .thenThrow(new InvalidRequestException("Validation failed"));

        mockMvc.perform(get("/api/staff/claims/unassigned").with(as(CLAIMS_STAFF)).param("market", "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void anUnsupportedSortFieldReportedByClaimServiceIs400() throws Exception {
        when(assessmentService.getUnassignedClaims(any(), any(Pageable.class)))
                .thenThrow(new InvalidRequestException("Unsupported sort field 'password'"));

        mockMvc.perform(get("/api/staff/claims/unassigned").with(as(CLAIMS_STAFF)).param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field 'password'"));
    }

    // ---- assign -----------------------------------------------------------------------------------------------

    @Test
    void assignReturnsTheAssessment() throws Exception {
        when(assessmentService.assign(10L, CLAIMS_STAFF)).thenReturn(assessment(ClaimStatus.UNDER_REVIEW, null));

        mockMvc.perform(post("/api/staff/claims/10/assign").with(as(CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(10))
                .andExpect(jsonPath("$.claimStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.staffId").value(7))
                .andExpect(jsonPath("$.staffName").value("Demo Claims Staff"))
                .andExpect(jsonPath("$.decision").doesNotExist());
    }

    // ---- approve ----------------------------------------------------------------------------------------------

    @Test
    void approveAcceptsACommentOrNoBody() throws Exception {
        when(assessmentService.approve(any(), any(), any())).thenReturn(assessment(ClaimStatus.APPROVED, AssessmentDecision.APPROVED));

        mockMvc.perform(post("/api/staff/claims/10/approve").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"Damage confirmed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"))
                .andExpect(jsonPath("$.decision").value("APPROVED"));
        verify(assessmentService).approve(10L, new ApproveClaimRequest("Damage confirmed"), CLAIMS_STAFF);

        mockMvc.perform(post("/api/staff/claims/11/approve").with(as(CLAIMS_STAFF))).andExpect(status().isOk());
        verify(assessmentService).approve(eq(11L), isNull(), eq(CLAIMS_STAFF));
    }

    @Test
    void approveRejectsAnOverlongComment() throws Exception {
        mockMvc.perform(post("/api/staff/claims/10/approve").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());

        verifyNoInteractions(assessmentService);
    }

    // ---- settle -----------------------------------------------------------------------------------------------

    @Test
    void settleAcceptsACommentOrNoBody() throws Exception {
        when(assessmentService.settle(any(), any(), any())).thenReturn(assessment(ClaimStatus.SETTLED, AssessmentDecision.APPROVED));

        mockMvc.perform(post("/api/staff/claims/10/settle").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"Paid in full"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("SETTLED"))
                .andExpect(jsonPath("$.decision").value("APPROVED"));
        verify(assessmentService).settle(10L, new SettleClaimRequest("Paid in full"), CLAIMS_STAFF);

        mockMvc.perform(post("/api/staff/claims/11/settle").with(as(CLAIMS_STAFF))).andExpect(status().isOk());
        verify(assessmentService).settle(eq(11L), isNull(), eq(CLAIMS_STAFF));
    }

    @Test
    void settleRejectsAnOverlongComment() throws Exception {
        mockMvc.perform(post("/api/staff/claims/10/settle").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());

        verifyNoInteractions(assessmentService);
    }

    // ---- reject -----------------------------------------------------------------------------------------------

    @Test
    void rejectRequiresAReason() throws Exception {
        for (String body : List.of("{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":null}")) {
            mockMvc.perform(post("/api/staff/claims/10/reject").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.reason").exists());
        }
        verifyNoInteractions(assessmentService);
    }

    @Test
    void rejectWithoutABodyIs400() throws Exception {
        mockMvc.perform(post("/api/staff/claims/10/reject").with(as(CLAIMS_STAFF))).andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void rejectPassesTheReasonThrough() throws Exception {
        when(assessmentService.reject(any(), any(), any())).thenReturn(assessment(ClaimStatus.REJECTED, AssessmentDecision.REJECTED));

        mockMvc.perform(post("/api/staff/claims/10/reject").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Policy had lapsed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"));
        verify(assessmentService).reject(10L, new RejectClaimRequest("Policy had lapsed"), CLAIMS_STAFF);
    }

    // ---- request-info -----------------------------------------------------------------------------------------

    @Test
    void requestInfoRequiresAMessage() throws Exception {
        mockMvc.perform(post("/api/staff/claims/10/request-info").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void requestInfoPassesTheMessageThrough() throws Exception {
        when(assessmentService.requestInfo(any(), any(), any())).thenReturn(assessment(ClaimStatus.MORE_INFO_REQUIRED, null));

        mockMvc.perform(post("/api/staff/claims/10/request-info").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Send photos"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("MORE_INFO_REQUIRED"));
        verify(assessmentService).requestInfo(10L, new RequestInfoRequest("Send photos"), CLAIMS_STAFF);
    }

    // ---- notes ------------------------------------------------------------------------------------------------

    @Test
    void addingANoteReturns201WithTheNote() throws Exception {
        when(assessmentService.addNote(eq(10L), any(AddNoteRequest.class), eq(CLAIMS_STAFF))).thenReturn(
                new NoteResponse(1L, 10L, 7L, "Demo Claims Staff", NoteType.GENERAL, "Workshop estimate looks inflated", NOW));

        mockMvc.perform(post("/api/staff/claims/10/notes").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Workshop estimate looks inflated"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.noteType").value("GENERAL"))
                .andExpect(jsonPath("$.content").value("Workshop estimate looks inflated"));
        verify(assessmentService).addNote(10L, new AddNoteRequest("Workshop estimate looks inflated"), CLAIMS_STAFF);
    }

    @Test
    void aNoteNeedsContentOfReasonableLength() throws Exception {
        for (String body : List.of("{}", "{\"content\":\"  \"}", "{\"content\":\"" + "x".repeat(2001) + "\"}")) {
            mockMvc.perform(post("/api/staff/claims/10/notes").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.content").exists());
        }
        verifyNoInteractions(assessmentService);
    }

    @Test
    void readingNotesReturnsTheTrailAsAPlainArray() throws Exception {
        when(assessmentService.getNotes(10L)).thenReturn(List.of(
                new NoteResponse(1L, 10L, 7L, "Demo Claims Staff", NoteType.INFO_REQUEST, "Send photos", NOW.minusSeconds(60)),
                new NoteResponse(2L, 10L, 8L, "Other Staff", NoteType.GENERAL, "Called the workshop", NOW)));

        mockMvc.perform(get("/api/staff/claims/10/notes").with(as(CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].claimId").value(10))
                .andExpect(jsonPath("$[0].staffId").value(7))
                .andExpect(jsonPath("$[0].staffName").value("Demo Claims Staff"))
                .andExpect(jsonPath("$[0].noteType").value("INFO_REQUEST"))
                .andExpect(jsonPath("$[0].content").value("Send photos"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-22T05:59:00Z"))
                .andExpect(jsonPath("$[1].noteType").value("GENERAL"));
    }

    @Test
    void aClaimWithNoNotesIsAnEmptyArray() throws Exception {
        when(assessmentService.getNotes(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/staff/claims/10/notes").with(as(CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readingTheNotesOfAMissingClaimOrDuringAnOutageKeepsTheirMeaning() throws Exception {
        when(assessmentService.getNotes(404L)).thenThrow(new ClaimNotFoundException("Claim '404' was not found"));
        when(assessmentService.getNotes(503L)).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));

        mockMvc.perform(get("/api/staff/claims/404/notes").with(as(CLAIMS_STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Claim '404' was not found"))
                .andExpect(jsonPath("$.path").value("/api/staff/claims/404/notes"));
        mockMvc.perform(get("/api/staff/claims/503/notes").with(as(CLAIMS_STAFF)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Claim service is unavailable"));
    }

    @Test
    void readingNotesWithANonNumericClaimIdIs400() throws Exception {
        mockMvc.perform(get("/api/staff/claims/abc/notes").with(as(CLAIMS_STAFF))).andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    // ---- error mapping ----------------------------------------------------------------------------------------

    @Test
    void claimServiceFailuresKeepTheirMeaningForTheStaffMember() throws Exception {
        when(assessmentService.assign(404L, CLAIMS_STAFF)).thenThrow(new ClaimNotFoundException("Claim '404' was not found"));
        when(assessmentService.assign(409L, CLAIMS_STAFF)).thenThrow(new ClaimConflictException("Only a SUBMITTED claim can be assigned"));
        when(assessmentService.assign(403L, CLAIMS_STAFF)).thenThrow(new ClaimAccessDeniedException("This claim is not assigned to you"));
        when(assessmentService.assign(503L, CLAIMS_STAFF)).thenThrow(new ClaimServiceUnavailableException("Claim service is unavailable"));
        when(assessmentService.assign(502L, CLAIMS_STAFF)).thenThrow(new ClaimServiceProtocolException("Claim service sent an unreadable response"));
        when(assessmentService.assign(500L, CLAIMS_STAFF)).thenThrow(new IllegalStateException("secret internals"));

        assertError(404L, 404, "Claim '404' was not found");
        assertError(409L, 409, "Only a SUBMITTED claim can be assigned");
        assertError(403L, 403, "This claim is not assigned to you");
        assertError(503L, 503, "Claim service is unavailable");
        assertError(502L, 502, "Claim service sent an unreadable response");
        assertError(500L, 500, "An unexpected error occurred");
    }

    // ---- who may call -----------------------------------------------------------------------------------------

    @Test
    void onlyClaimsStaffMayUseTheStaffApi() throws Exception {
        for (AuthenticatedUser caller : List.of(MANAGER, CLAIMANT)) {
            mockMvc.perform(get("/api/staff/claims/unassigned").with(as(caller))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/staff/claims/10/notes").with(as(caller)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            for (String action : List.of("assign", "approve", "reject", "request-info", "settle", "notes")) {
                mockMvc.perform(post("/api/staff/claims/10/" + action).with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"x\",\"message\":\"x\",\"content\":\"x\"}"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403));
            }
        }
        verifyNoInteractions(assessmentService);
    }

    @Test
    void theStaffApiNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/staff/claims/unassigned"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/staff/claims/unassigned"));
        mockMvc.perform(post("/api/staff/claims/10/assign")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/claims/10/notes")).andExpect(status().isUnauthorized());
    }

    @Test
    void aNonNumericClaimIdIs400() throws Exception {
        mockMvc.perform(post("/api/staff/claims/abc/assign").with(as(CLAIMS_STAFF))).andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private void assertError(long claimId, int expectedStatus, String expectedMessage) throws Exception {
        mockMvc.perform(post("/api/staff/claims/" + claimId + "/assign").with(as(CLAIMS_STAFF)))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value("/api/staff/claims/" + claimId + "/assign"));
    }

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, "the-token", user.authorities()));
    }

    private static PageResponse<ClaimSummaryResponse> queue() {
        return new PageResponse<>(List.of(new ClaimSummaryResponse(10L, "CLM-2026-000010", "POL-2026-1", "AUTO", "SG",
                ClaimStatus.SUBMITTED, new BigDecimal("2500.00"), "SGD", LocalDate.of(2026, 10, 1), null, NOW, NOW)),
                0, 20, 1, 1, true, true);
    }

    private static AssessmentResponse assessment(ClaimStatus claimStatus, AssessmentDecision decision) {
        return new AssessmentResponse(10L, "CLM-2026-000010", claimStatus, 7L, "Demo Claims Staff", NOW, decision,
                decision == null ? null : "reason", decision == null ? null : NOW);
    }
}
