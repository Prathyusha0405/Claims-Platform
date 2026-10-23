package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.config.SecurityConfig;
import com.chubb.claims.claim.dto.ApproveClaimRequest;
import com.chubb.claims.claim.dto.ClaimStatisticsResponse;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.StaffStatusTotal;
import com.chubb.claims.claim.dto.RejectClaimRequest;
import com.chubb.claims.claim.dto.RequestInfoRequest;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.exception.NotAssignedStaffException;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.JwtService;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.service.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalClaimController.class)
@Import(SecurityConfig.class)
class InternalClaimControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private static final AuthenticatedUser CLAIMS_STAFF = new AuthenticatedUser(7L, "staff@chubb.com", "Demo Claims Staff", Role.CLAIMS_STAFF);
    private static final AuthenticatedUser MANAGER = new AuthenticatedUser(8L, "manager@chubb.com", "Demo Manager", Role.MANAGER);
    private static final AuthenticatedUser CLAIMANT = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private JwtService jwtService;

    // ---- staff member actions --------------------------------------------------------------------------------------

    @Test
    void assignReturnsTheUpdatedClaim() throws Exception {
        when(claimService.assignToStaff(10L, CLAIMS_STAFF)).thenReturn(summary(ClaimStatus.UNDER_REVIEW, 7L));

        mockMvc.perform(post("/api/internal/claims/10/assign").with(as(CLAIMS_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedStaffId").value(7));
    }

    @Test
    void approveAcceptsACommentOrNoBodyAtAll() throws Exception {
        when(claimService.approve(any(), any(), any())).thenReturn(summary(ClaimStatus.APPROVED, 7L));

        mockMvc.perform(post("/api/internal/claims/10/approve").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"Damage confirmed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        verify(claimService).approve(10L, new ApproveClaimRequest("Damage confirmed"), CLAIMS_STAFF);

        mockMvc.perform(post("/api/internal/claims/11/approve").with(as(CLAIMS_STAFF))).andExpect(status().isOk());
        verify(claimService).approve(org.mockito.ArgumentMatchers.eq(11L), isNull(), org.mockito.ArgumentMatchers.eq(CLAIMS_STAFF));
    }

    @Test
    void approveRejectsAnOverlongComment() throws Exception {
        mockMvc.perform(post("/api/internal/claims/10/approve").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void rejectNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/internal/claims/10/reject").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void rejectPassesTheReasonThrough() throws Exception {
        when(claimService.reject(any(), any(), any())).thenReturn(summary(ClaimStatus.REJECTED, 7L));

        mockMvc.perform(post("/api/internal/claims/10/reject").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Policy had lapsed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        verify(claimService).reject(10L, new RejectClaimRequest("Policy had lapsed"), CLAIMS_STAFF);
    }

    @Test
    void requestInfoNeedsAMessage() throws Exception {
        mockMvc.perform(post("/api/internal/claims/10/request-info").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void requestInfoPassesTheMessageThrough() throws Exception {
        when(claimService.requestAdditionalInfo(any(), any(), any())).thenReturn(summary(ClaimStatus.MORE_INFO_REQUIRED, 7L));

        mockMvc.perform(post("/api/internal/claims/10/request-info").with(as(CLAIMS_STAFF)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Send photos"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MORE_INFO_REQUIRED"));
        verify(claimService).requestAdditionalInfo(10L, new RequestInfoRequest("Send photos"), CLAIMS_STAFF);
    }

    @Test
    void anotherStaffMembersClaimIs403AsJson() throws Exception {
        when(claimService.approve(any(), any(), any())).thenThrow(new NotAssignedStaffException());

        mockMvc.perform(post("/api/internal/claims/10/approve").with(as(CLAIMS_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This claim is not assigned to you"));
    }

    @Test
    void anInvalidTransitionIs409() throws Exception {
        when(claimService.assignToStaff(any(), any()))
                .thenThrow(new InvalidStatusTransitionException("Only a SUBMITTED claim can be assigned to a claims staff member (current status: APPROVED)"));

        mockMvc.perform(post("/api/internal/claims/10/assign").with(as(CLAIMS_STAFF)))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownClaimIs404() throws Exception {
        when(claimService.assignToStaff(any(), any())).thenThrow(new ClaimNotFoundException("99"));

        mockMvc.perform(post("/api/internal/claims/99/assign").with(as(CLAIMS_STAFF))).andExpect(status().isNotFound());
    }

    @Test
    void onlyClaimsStaffMayPerformActions() throws Exception {
        for (AuthenticatedUser caller : List.of(MANAGER, CLAIMANT)) {
            for (String action : List.of("assign", "approve", "reject", "request-info")) {
                mockMvc.perform(post("/api/internal/claims/10/" + action).with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"x\",\"message\":\"x\"}"))
                        .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(claimService);
    }

    @Test
    void actionsNeedAToken() throws Exception {
        mockMvc.perform(post("/api/internal/claims/10/assign")).andExpect(status().isUnauthorized());
    }

    // ---- reads ------------------------------------------------------------------------------------------------

    @Test
    void claimsStaffAndManagersCanReadAClaimById() throws Exception {
        when(claimService.getClaimById(10L)).thenReturn(summary(ClaimStatus.SUBMITTED, null));

        for (AuthenticatedUser caller : List.of(CLAIMS_STAFF, MANAGER)) {
            mockMvc.perform(get("/api/internal/claims/10").with(as(caller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000010"));
        }
    }

    @Test
    void claimantsCannotUseTheInternalReads() throws Exception {
        mockMvc.perform(get("/api/internal/claims/10").with(as(CLAIMANT))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/internal/claims/statistics").with(as(CLAIMANT))).andExpect(status().isForbidden());
        verifyNoInteractions(claimService);
    }

    @Test
    void statisticsAreReadableByClaimsStaffAndManagers() throws Exception {
        when(claimService.statistics()).thenReturn(new ClaimStatisticsResponse(
                List.of(new StatusTotal(Market.JP, Currency.JPY, ClaimStatus.SUBMITTED, 2, new BigDecimal("30000.00"))),
                List.of(new StaffStatusTotal(7L, Market.JP, Currency.JPY, ClaimStatus.UNDER_REVIEW, 1, new BigDecimal("10000.00")))));

        for (AuthenticatedUser caller : List.of(CLAIMS_STAFF, MANAGER)) {
            mockMvc.perform(get("/api/internal/claims/statistics").with(as(caller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.byStatus[0].market").value("JP"))
                    .andExpect(jsonPath("$.byStatus[0].currency").value("JPY"))
                    .andExpect(jsonPath("$.byStatus[0].status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.byStatus[0].claimCount").value(2))
                    .andExpect(jsonPath("$.byStatus[0].totalAmount").value(30000.00))
                    .andExpect(jsonPath("$.byStaff[0].staffId").value(7))
                    .andExpect(jsonPath("$.byStaff[0].market").value("JP"))
                    .andExpect(jsonPath("$.byStaff[0].currency").value("JPY"))
                    .andExpect(jsonPath("$.byStaff[0].status").value("UNDER_REVIEW"));
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private static ClaimSummaryResponse summary(ClaimStatus status, Long staffId) {
        return new ClaimSummaryResponse(10L, "CLM-2026-000010", "POL-2026-1", ClaimType.AUTO, Market.SG, status,
                new BigDecimal("2500.00"), Currency.SGD, LocalDate.of(2026, 10, 1), staffId, NOW, NOW);
    }
}
