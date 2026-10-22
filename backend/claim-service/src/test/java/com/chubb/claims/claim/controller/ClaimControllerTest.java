package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.config.SecurityConfig;
import com.chubb.claims.claim.dto.AdditionalInfoRequest;
import com.chubb.claims.claim.dto.ClaimDetailResponse;
import com.chubb.claims.claim.dto.ClaimHistoryResponse;
import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.CreateClaimRequest;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.exception.ClaimNotFoundException;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.JwtService;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.service.ClaimService;
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

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
class ClaimControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private static final AuthenticatedUser CLAIMANT = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(8L, "admin@chubb.com", "Demo Admin", Role.ADMIN);

    private static final String VALID_CLAIM = """
            {"policyNumber":"POL-2026-12345","claimType":"AUTO","incidentDate":"2026-10-15",
             "incidentLocation":"Orchard Road","description":"Rear-ended at a traffic light","claimAmount":2500.00}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private JwtService jwtService;

    // ---- POST /api/claims -------------------------------------------------------------------------------------

    @Test
    void createReturns201WithLocationAndTheNewClaim() throws Exception {
        when(claimService.createClaim(any(CreateClaimRequest.class), eq(CLAIMANT))).thenReturn(detail());

        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/claims/CLM-2026-000001")))
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000001"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimAmount").value(2500.00))
                .andExpect(jsonPath("$.history[0].eventType").value("CLAIM_CREATED"));

        ArgumentCaptor<CreateClaimRequest> request = ArgumentCaptor.forClass(CreateClaimRequest.class);
        verify(claimService).createClaim(request.capture(), eq(CLAIMANT));
        assertThat(request.getValue()).isEqualTo(new CreateClaimRequest("POL-2026-12345", ClaimType.AUTO,
                LocalDate.of(2026, 10, 15), "Orchard Road", "Rear-ended at a traffic light", new BigDecimal("2500.00")));
    }

    @Test
    void createReportsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNumber":"x","claimAmount":-5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.policyNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.claimType").exists())
                .andExpect(jsonPath("$.fieldErrors.incidentDate").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.claimAmount").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void createRejectsAmountsWithMoreThanTwoDecimals() throws Exception {
        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM.replace("2500.00", "2500.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimAmount").exists());
    }

    @Test
    void createRejectsAnUnknownClaimType() throws Exception {
        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLAIM.replace("AUTO", "SPACESHIP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSurfacesServiceLevelValidation() throws Exception {
        when(claimService.createClaim(any(CreateClaimRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new InvalidRequestException("incidentDate cannot be in the future"));

        mockMvc.perform(post("/api/claims").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("incidentDate cannot be in the future"));
    }

    @Test
    void onlyClaimantsCanCreateClaims() throws Exception {
        for (AuthenticatedUser staff : List.of(OFFICER, ADMIN)) {
            mockMvc.perform(post("/api/claims").with(as(staff)).contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        verifyNoInteractions(claimService);
    }

    @Test
    void createWithoutATokenIs401AsJson() throws Exception {
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/claims"));
    }

    // ---- GET /api/claims/my-claims ----------------------------------------------------------------------------

    @Test
    void myClaimsReturnsAPageAndDefaultsToNewestFirst() throws Exception {
        when(claimService.getMyClaims(eq(CLAIMANT), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/claims/my-claims").with(as(CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimNumber").value("CLM-2026-000001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(claimService).getMyClaims(eq(CLAIMANT), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"));
    }

    @Test
    void myClaimsPassesPageSizeAndSortThrough() throws Exception {
        when(claimService.getMyClaims(eq(CLAIMANT), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/claims/my-claims").with(as(CLAIMANT))
                        .param("page", "2").param("size", "5").param("sort", "claimAmount,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(claimService).getMyClaims(eq(CLAIMANT), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.asc("claimAmount"));
    }

    @Test
    void myClaimsCapsThePageSizeAt100() throws Exception {
        when(claimService.getMyClaims(eq(CLAIMANT), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/claims/my-claims").with(as(CLAIMANT)).param("size", "100000")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(claimService).getMyClaims(eq(CLAIMANT), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void myClaimsIsForClaimantsOnly() throws Exception {
        mockMvc.perform(get("/api/claims/my-claims").with(as(OFFICER))).andExpect(status().isForbidden());
        verifyNoInteractions(claimService);
    }

    // ---- GET /api/claims/search -------------------------------------------------------------------------------

    @Test
    void searchBindsEveryFilterAndPassesThePrincipal() throws Exception {
        when(claimService.search(any(ClaimSearchCriteria.class), eq(OFFICER), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/claims/search").with(as(OFFICER))
                        .param("claimNumber", "CLM-2026").param("policyNumber", "POL-1").param("status", "UNDER_REVIEW")
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-31").param("assignedOfficerId", "7")
                        .param("page", "1").param("size", "10").param("sort", "status,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimNumber").value("CLM-2026-000001"));

        ArgumentCaptor<ClaimSearchCriteria> criteria = ArgumentCaptor.forClass(ClaimSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(claimService).search(criteria.capture(), eq(OFFICER), pageable.capture());
        assertThat(criteria.getValue()).isEqualTo(new ClaimSearchCriteria("CLM-2026", "POL-1", ClaimStatus.UNDER_REVIEW,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 7L));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("status"));
    }

    @Test
    void searchWithNoFiltersBindsAnEmptyCriteria() throws Exception {
        when(claimService.search(any(ClaimSearchCriteria.class), eq(CLAIMANT), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/claims/search").with(as(CLAIMANT))).andExpect(status().isOk());

        ArgumentCaptor<ClaimSearchCriteria> criteria = ArgumentCaptor.forClass(ClaimSearchCriteria.class);
        verify(claimService).search(criteria.capture(), eq(CLAIMANT), any(Pageable.class));
        assertThat(criteria.getValue()).isEqualTo(new ClaimSearchCriteria(null, null, null, null, null, null));
    }

    @Test
    void searchIsOpenToAdminsAndClaimantsToo() throws Exception {
        when(claimService.search(any(ClaimSearchCriteria.class), any(AuthenticatedUser.class), any(Pageable.class)))
                .thenReturn(onePage());

        mockMvc.perform(get("/api/claims/search").with(as(ADMIN))).andExpect(status().isOk());
        mockMvc.perform(get("/api/claims/search").with(as(CLAIMANT))).andExpect(status().isOk());
    }

    @Test
    void searchRejectsAnUnknownStatusWithAFriendlyMessage() throws Exception {
        mockMvc.perform(get("/api/claims/search").with(as(OFFICER)).param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.status").value(
                        "Invalid value 'BOGUS'; allowed values: [SUBMITTED, UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED, SETTLED]"));

        verifyNoInteractions(claimService);
    }

    @Test
    void searchRejectsAMalformedDateWithoutLeakingInternalClassNames() throws Exception {
        mockMvc.perform(get("/api/claims/search").with(as(OFFICER)).param("fromDate", "01/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fromDate").value("Invalid value '01/10/2026'"));

        verifyNoInteractions(claimService);
    }

    @Test
    void searchRejectsANonNumericOfficerIdWith400() throws Exception {
        mockMvc.perform(get("/api/claims/search").with(as(OFFICER)).param("assignedOfficerId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchSurfacesServiceLevelValidation() throws Exception {
        when(claimService.search(any(ClaimSearchCriteria.class), any(AuthenticatedUser.class), any(Pageable.class)))
                .thenThrow(new InvalidRequestException("fromDate must not be after toDate"));

        mockMvc.perform(get("/api/claims/search").with(as(OFFICER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
    }

    @Test
    void searchIsNotCapturedByTheClaimNumberRoute() throws Exception {
        when(claimService.search(any(ClaimSearchCriteria.class), any(AuthenticatedUser.class), any(Pageable.class)))
                .thenReturn(onePage());

        mockMvc.perform(get("/api/claims/search").with(as(OFFICER))).andExpect(status().isOk());

        verify(claimService, org.mockito.Mockito.never()).getClaim(any(), any());
    }

    // ---- GET /api/claims/{claimNumber} ------------------------------------------------------------------------

    @Test
    void trackingReturnsTheClaimWithItsHistory() throws Exception {
        when(claimService.getClaim("CLM-2026-000001", OFFICER)).thenReturn(detail());

        mockMvc.perform(get("/api/claims/CLM-2026-000001").with(as(OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000001"))
                .andExpect(jsonPath("$.claimantEmail").value("jane@example.com"))
                .andExpect(jsonPath("$.history.length()").value(1));
    }

    @Test
    void trackingAnUnknownClaimIs404AsJson() throws Exception {
        when(claimService.getClaim("CLM-2026-999999", CLAIMANT)).thenThrow(new ClaimNotFoundException("CLM-2026-999999"));

        mockMvc.perform(get("/api/claims/CLM-2026-999999").with(as(CLAIMANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Claim 'CLM-2026-999999' was not found"))
                .andExpect(jsonPath("$.path").value("/api/claims/CLM-2026-999999"));
    }

    @Test
    void trackingNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/claims/CLM-2026-000001")).andExpect(status().isUnauthorized());
    }

    // ---- POST /api/claims/{id}/additional-info ----------------------------------------------------------------

    @Test
    void additionalInfoReturnsTheUpdatedClaim() throws Exception {
        when(claimService.submitAdditionalInfo(eq(10L), any(AdditionalInfoRequest.class), eq(CLAIMANT))).thenReturn(detail());

        mockMvc.perform(post("/api/claims/10/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Estimate attached"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000001"));

        verify(claimService).submitAdditionalInfo(10L, new AdditionalInfoRequest("Estimate attached"), CLAIMANT);
    }

    @Test
    void additionalInfoRequiresAMessage() throws Exception {
        mockMvc.perform(post("/api/claims/10/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void additionalInfoRejectsANonNumericClaimId() throws Exception {
        mockMvc.perform(post("/api/claims/abc/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(claimService);
    }

    @Test
    void additionalInfoOnAClaimThatIsNotAwaitingItIs409() throws Exception {
        when(claimService.submitAdditionalInfo(eq(10L), any(AdditionalInfoRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new InvalidStatusTransitionException("Additional information can only be submitted while the claim is MORE_INFO_REQUIRED (current status: SUBMITTED)"));

        mockMvc.perform(post("/api/claims/10/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void additionalInfoOnSomeoneElsesClaimIs404() throws Exception {
        when(claimService.submitAdditionalInfo(eq(10L), any(AdditionalInfoRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new ClaimNotFoundException("10"));

        mockMvc.perform(post("/api/claims/10/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void additionalInfoIsForClaimantsOnly() throws Exception {
        mockMvc.perform(post("/api/claims/10/additional-info").with(as(OFFICER)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    // ---- global error handling --------------------------------------------------------------------------------

    @Test
    void unexpectedFailuresAre500WithoutLeakingInternals() throws Exception {
        when(claimService.getClaim(any(), any())).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/claims/CLM-2026-000001").with(as(CLAIMANT)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void concurrentModificationIs409() throws Exception {
        when(claimService.submitAdditionalInfo(eq(10L), any(AdditionalInfoRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        com.chubb.claims.claim.entity.Claim.class, 10L));

        mockMvc.perform(post("/api/claims/10/additional-info").with(as(CLAIMANT)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The claim was changed by another request; reload it and try again"));
    }

    @Test
    void unknownPathsAre404InTheSameShape() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(as(CLAIMANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));
    }

    @Test
    void unsupportedMethodsAre405InTheSameShape() throws Exception {
        mockMvc.perform(delete("/api/claims").with(as(CLAIMANT)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private static PageResponse<ClaimSummaryResponse> onePage() {
        return new PageResponse<>(List.of(new ClaimSummaryResponse(10L, "CLM-2026-000001", "POL-2026-12345", ClaimType.AUTO,
                ClaimStatus.SUBMITTED, new BigDecimal("2500.00"), LocalDate.of(2026, 10, 15), null, NOW, NOW)),
                0, 20, 1, 1, true, true);
    }

    private static ClaimDetailResponse detail() {
        return new ClaimDetailResponse(10L, "CLM-2026-000001", "POL-2026-12345", 5L, "Jane Tan", "jane@example.com",
                ClaimType.AUTO, ClaimStatus.SUBMITTED, new BigDecimal("2500.00"), LocalDate.of(2026, 10, 15),
                "Orchard Road", "Rear-ended at a traffic light", null, NOW, NOW,
                List.of(new ClaimHistoryResponse(ClaimEventType.CLAIM_CREATED, null, ClaimStatus.SUBMITTED, 5L, "CLAIMANT",
                        "Claim submitted", NOW)));
    }
}
