package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.dto.ErrorResponse;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.OfficerWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Management dashboards (ADMIN only). Figures are computed live from claim-service.")
@ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "503", description = "Claim service is unavailable",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/status-summary")
    @Operation(summary = "Claims by status",
            description = "Total, pending (SUBMITTED + UNDER_REVIEW + MORE_INFO_REQUIRED), approved, rejected and settled "
                    + "claims, plus the count for every status.")
    public StatusSummaryResponse statusSummary() {
        return dashboardService.statusSummary();
    }

    @GetMapping("/officer-workload")
    @Operation(summary = "Officer workload",
            description = "Per officer: open claims (under review and awaiting information), decided claims and the "
                    + "exposure of the open ones; plus how many claims still wait in the work queue.")
    public OfficerWorkloadResponse officerWorkload() {
        return dashboardService.officerWorkload();
    }

    @GetMapping("/liability-exposure")
    @Operation(summary = "Liability exposure",
            description = "SUM(claimAmount) over every claim whose status is not SETTLED, with a breakdown by status.")
    public LiabilityExposureResponse liabilityExposure() {
        return dashboardService.liabilityExposure();
    }
}
