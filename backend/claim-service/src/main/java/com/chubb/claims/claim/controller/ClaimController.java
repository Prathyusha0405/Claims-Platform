package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.dto.AdditionalInfoRequest;
import com.chubb.claims.claim.dto.ClaimDetailResponse;
import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.dto.CreateClaimRequest;
import com.chubb.claims.claim.dto.ErrorResponse;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Claims", description = "Submit, track and search insurance claims")
public class ClaimController {

    /** Stated on every @PageableDefault: that annotation's own default (10) would otherwise win over any property. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final String SORT_HELP = "Sort with `sort=<field>,<asc|desc>` (repeatable). Allowed fields: "
            + "`createdAt` (default, newest first), `updatedAt`, `claimNumber`, `policyNumber`, `status`, "
            + "`claimAmount`, `incidentDate`. Pages are zero-based, 20 per page by default; the maximum page size is 100.";

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(summary = "Submit a new claim",
            description = "CLAIMANT only. The claim starts in SUBMITTED status and receives a claim number such as CLM-2026-000042.")
    @ApiResponse(responseCode = "201", description = "Claim created; Location header points at the new claim")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a CLAIMANT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ClaimDetailResponse> create(@Valid @RequestBody CreateClaimRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        ClaimDetailResponse created = claimService.createClaim(request, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{claimNumber}").buildAndExpand(created.claimNumber()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/my-claims")
    @Operation(summary = "List my claims",
            description = "CLAIMANT only. The caller's own claims, paged. " + SORT_HELP)
    @ApiResponse(responseCode = "400", description = "Unsupported sort field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a CLAIMANT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<ClaimSummaryResponse> myClaims(
            @ParameterObject @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return claimService.getMyClaims(user, pageable);
    }

    @GetMapping("/search")
    @Operation(summary = "Search claims",
            description = "All filters are optional and combined with AND. Claimants only ever see their own claims; "
                    + "officers and admins search all claims. `fromDate`/`toDate` filter on the submission date (UTC, "
                    + "both inclusive). " + SORT_HELP)
    @ApiResponse(responseCode = "400", description = "Invalid filter, date range or sort field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<ClaimSummaryResponse> search(
            @ParameterObject ClaimSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return claimService.search(criteria, user, pageable);
    }

    @GetMapping("/{claimNumber}")
    @Operation(summary = "Track a claim by claim number",
            description = "Returns the claim with its full history timeline. Claimants can only open their own claims.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such claim, or it belongs to another claimant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimDetailResponse getByClaimNumber(@PathVariable String claimNumber,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return claimService.getClaim(claimNumber, user);
    }

    @PostMapping("/{id}/additional-info")
    @Operation(summary = "Provide additional information requested by an officer",
            description = "CLAIMANT only, on their own claim, and only while it is MORE_INFO_REQUIRED. Moves the claim "
                    + "back to UNDER_REVIEW.")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a CLAIMANT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such claim, or it belongs to another claimant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Claim is not awaiting additional information",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ClaimDetailResponse submitAdditionalInfo(@PathVariable Long id,
                                                    @Valid @RequestBody AdditionalInfoRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return claimService.submitAdditionalInfo(id, request, user);
    }
}
