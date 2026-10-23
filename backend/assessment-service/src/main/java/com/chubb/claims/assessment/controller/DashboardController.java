package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.dto.ErrorResponse;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Management dashboards (MANAGER only). Figures are computed live from claim-service. "
        + "Each can be limited to one market with `market`; money is reported in the reporting currency, converted at "
        + "indicative fixed rates.")
@ApiResponse(responseCode = "403", description = "Caller is not a MANAGER",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "503", description = "Claim service is unavailable",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class DashboardController {

    private static final String MARKET_HELP = "Limit the figures to one market, by ISO country code; omit for every market";

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/status-summary")
    @Operation(summary = "Claims by status",
            description = "Total, pending (SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED), approved, rejected and settled "
                    + "claims, plus the count for every status.")
    public StatusSummaryResponse statusSummary(@Parameter(description = MARKET_HELP, example = "SG", schema = @Schema(allowableValues = {"AU", "HK", "JP", "MY", "NZ", "SG"}))
            @RequestParam(required = false) String market) {
        return dashboardService.statusSummary(market);
    }

    @GetMapping("/staff-workload")
    @Operation(summary = "Claims staff workload",
            description = "Per claims staff member: open claims (under review and awaiting information), decided claims and the "
                    + "exposure of the open ones; plus how many claims still wait in the work queue.")
    public StaffWorkloadResponse staffWorkload(@Parameter(description = MARKET_HELP, example = "SG", schema = @Schema(allowableValues = {"AU", "HK", "JP", "MY", "NZ", "SG"}))
            @RequestParam(required = false) String market) {
        return dashboardService.staffWorkload(market);
    }

    @GetMapping("/liability-exposure")
    @Operation(summary = "Liability exposure",
            description = "SUM(claimAmount) over every claim whose status is not SETTLED, converted to the reporting currency, "
                    + "with a breakdown by status and, in its own currency too, by market.")
    public LiabilityExposureResponse liabilityExposure(@Parameter(description = MARKET_HELP, example = "SG", schema = @Schema(allowableValues = {"AU", "HK", "JP", "MY", "NZ", "SG"}))
            @RequestParam(required = false) String market) {
        return dashboardService.liabilityExposure(market);
    }
}
