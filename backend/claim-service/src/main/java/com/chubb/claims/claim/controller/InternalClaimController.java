package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.dto.ApproveClaimRequest;
import com.chubb.claims.claim.dto.ClaimStatisticsResponse;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.ClaimTrendResponse;
import com.chubb.claims.claim.dto.ErrorResponse;
import com.chubb.claims.claim.dto.RejectClaimRequest;
import com.chubb.claims.claim.dto.RequestInfoRequest;
import com.chubb.claims.claim.dto.SettleClaimRequest;
import com.chubb.claims.claim.dto.StatisticsCriteria;
import com.chubb.claims.claim.dto.TrendCriteria;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.service.ClaimService;
import com.chubb.claims.claim.service.ClaimTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API used by assessment-service, which forwards the staff member's own token. Claim-service stays the
 * only owner of the claim lifecycle: every transition here goes through the same state machine, audit trail and
 * notifications as any other change. Not intended for browser clients.
 */
@RestController
@RequestMapping("/api/internal/claims")
@Tag(name = "Internal (assessment-service)", description = "Claims staff actions and statistics consumed by assessment-service")
public class InternalClaimController {

    private final ClaimService claimService;
    private final ClaimTrendService claimTrendService;

    public InternalClaimController(ClaimService claimService, ClaimTrendService claimTrendService) {
        this.claimService = claimService;
        this.claimTrendService = claimTrendService;
    }

    @GetMapping("/statistics")
    @Operation(summary = "Claim totals per market and status, per staff member, and per claim type",
            description = "CLAIMS_STAFF or MANAGER. Raw aggregates, each in its own market's currency; assessment-service converts "
                    + "and derives its dashboard metrics from them. `claimType`, `fromDate` and `toDate` (submission date, UTC, both "
                    + "inclusive) narrow them; the per-type totals follow the dates but never `claimType`.")
    @ApiResponse(responseCode = "400", description = "Unknown claim type, malformed date, or fromDate after toDate",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a CLAIMS_STAFF or MANAGER",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimStatisticsResponse statistics(@ParameterObject StatisticsCriteria criteria) {
        return claimService.statistics(criteria);
    }

    @GetMapping("/trend")
    @Operation(summary = "Claims submitted per week or month, by current status",
            description = "CLAIMS_STAFF or MANAGER. Periods are gap-filled and in UTC; the size of a period follows the window "
                    + "(weeks up to 183 days, months beyond). `market`, `claimType`, `fromDate` and `toDate` narrow the claims.")
    @ApiResponse(responseCode = "400", description = "Unknown market or claim type, malformed date, or fromDate after toDate",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a CLAIMS_STAFF or MANAGER",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimTrendResponse trend(@ParameterObject TrendCriteria criteria) {
        return claimTrendService.trend(criteria);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a claim by id", description = "CLAIMS_STAFF or MANAGER.")
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse getById(@PathVariable Long id) {
        return claimService.getClaimById(id);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign a claim to the calling staff member",
            description = "CLAIMS_STAFF only. SUBMITTED -> UNDER_REVIEW and publishes CLAIM_ASSIGNED.")
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not SUBMITTED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse assign(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return claimService.assignToStaff(id, staffMember);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a claim",
            description = "CLAIMS_STAFF assigned to the claim. UNDER_REVIEW -> APPROVED and publishes CLAIM_APPROVED.")
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse approve(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid ApproveClaimRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return claimService.approve(id, request, staffMember);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim",
            description = "CLAIMS_STAFF assigned to the claim. UNDER_REVIEW -> REJECTED and publishes CLAIM_REJECTED.")
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse reject(@PathVariable Long id, @Valid @RequestBody RejectClaimRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return claimService.reject(id, request, staffMember);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Settle a claim",
            description = "CLAIMS_STAFF assigned to the claim. APPROVED or REJECTED -> SETTLED and publishes CLAIM_SETTLED. "
                    + "The optional comment is saved in the claim history.")
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not APPROVED or REJECTED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse settle(@PathVariable Long id,
                                       @RequestBody(required = false) @Valid SettleClaimRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return claimService.settle(id, request, staffMember);
    }

    @PostMapping("/{id}/request-info")
    @Operation(summary = "Ask the claimant for more information",
            description = "CLAIMS_STAFF assigned to the claim. UNDER_REVIEW -> MORE_INFO_REQUIRED and publishes "
                    + "ADDITIONAL_INFO_REQUESTED.")
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimSummaryResponse requestInfo(@PathVariable Long id, @Valid @RequestBody RequestInfoRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return claimService.requestAdditionalInfo(id, request, staffMember);
    }
}
