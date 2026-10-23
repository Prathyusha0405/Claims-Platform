package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.dto.AddNoteRequest;
import com.chubb.claims.assessment.dto.ApproveClaimRequest;
import com.chubb.claims.assessment.dto.AssessmentResponse;
import com.chubb.claims.assessment.dto.ClaimSummaryResponse;
import com.chubb.claims.assessment.dto.ErrorResponse;
import com.chubb.claims.assessment.dto.NoteResponse;
import com.chubb.claims.assessment.dto.PageResponse;
import com.chubb.claims.assessment.dto.RejectClaimRequest;
import com.chubb.claims.assessment.dto.RequestInfoRequest;
import com.chubb.claims.assessment.security.AuthenticatedUser;
import com.chubb.claims.assessment.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff/claims")
@Tag(name = "Claims staff", description = "The claims staff work queue, decisions and review notes (CLAIMS_STAFF only)")
public class StaffClaimController {

    /** Stated on the annotation: its own default (10) would otherwise win over any property. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AssessmentService assessmentService;

    public StaffClaimController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/unassigned")
    @Operation(summary = "Work queue: claims waiting for a staff member",
            description = "SUBMITTED claims nobody has taken yet, oldest first by default. Narrow it to one market with "
                    + "`market`. Sort with `sort=<field>,<asc|desc>`; allowed fields: `createdAt`, `updatedAt`, "
                    + "`claimNumber`, `policyNumber`, `market`, `status`, `claimAmount`, `incidentDate`. Pages are "
                    + "zero-based, 20 per page, at most 100.")
    @ApiResponse(responseCode = "400", description = "Unsupported sort field or unknown market",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Claim service is unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<ClaimSummaryResponse> unassigned(
            @Parameter(description = "Only claims filed in this market, by ISO country code", example = "SG",
                    schema = @Schema(allowableValues = {"AU", "HK", "JP", "MY", "NZ", "SG"}))
            @RequestParam(required = false) String market,
            @ParameterObject @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return assessmentService.getUnassignedClaims(market, pageable);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Take an unassigned claim",
            description = "Assigns the claim to you and moves it SUBMITTED -> UNDER_REVIEW. The claimant is notified.")
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not SUBMITTED (already taken or decided)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Claim service is unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AssessmentResponse assign(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return assessmentService.assign(id, staffMember);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a claim",
            description = "Only the staff member the claim is assigned to. UNDER_REVIEW -> APPROVED. The comment is optional "
                    + "and is saved in the claim history and your review notes.")
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AssessmentResponse approve(@PathVariable Long id,
                                      @RequestBody(required = false) @Valid ApproveClaimRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return assessmentService.approve(id, request, staffMember);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim",
            description = "Only the staff member the claim is assigned to. UNDER_REVIEW -> REJECTED. A reason is required "
                    + "and the claimant can see it in the claim history.")
    @ApiResponse(responseCode = "400", description = "Reason missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AssessmentResponse reject(@PathVariable Long id, @Valid @RequestBody RejectClaimRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return assessmentService.reject(id, request, staffMember);
    }

    @PostMapping("/{id}/request-info")
    @Operation(summary = "Ask the claimant for more information",
            description = "Only the staff member the claim is assigned to. UNDER_REVIEW -> MORE_INFO_REQUIRED. The message is "
                    + "required; the claimant is notified and answers through the claim service.")
    @ApiResponse(responseCode = "400", description = "Message missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Claim is assigned to another staff member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AssessmentResponse requestInfo(@PathVariable Long id, @Valid @RequestBody RequestInfoRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return assessmentService.requestInfo(id, request, staffMember);
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "Read a claim's review notes",
            description = "The internal trail for the claim, oldest first: general notes and the notes recorded when a "
                    + "claim is approved, rejected or more information is requested. Any claims staff member can read any existing "
                    + "claim's notes. Never shown to claimants. Not paged: a claim has only as many notes as its reviews.")
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Claim service is unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<NoteResponse> notes(@PathVariable Long id) {
        return assessmentService.getNotes(id);
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an internal note to a claim",
            description = "Any claims staff member can note any existing claim. Notes are internal and never shown to claimants.")
    @ApiResponse(responseCode = "400", description = "Note content missing or too long",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such claim",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public NoteResponse addNote(@PathVariable Long id, @Valid @RequestBody AddNoteRequest request,
                                @AuthenticationPrincipal AuthenticatedUser staffMember) {
        return assessmentService.addNote(id, request, staffMember);
    }
}
