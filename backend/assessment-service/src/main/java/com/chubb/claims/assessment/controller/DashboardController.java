package com.chubb.claims.assessment.controller;

import com.chubb.claims.assessment.dto.ClaimsOverTimeResponse;
import com.chubb.claims.assessment.dto.DashboardCriteria;
import com.chubb.claims.assessment.dto.ErrorResponse;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.StaffWorkloadResponse;
import com.chubb.claims.assessment.dto.StatusSummaryResponse;
import com.chubb.claims.assessment.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Management dashboards (MANAGER only). Figures are computed live from claim-service. "
        + "Each can be limited to one market (`market`), one claim type (`claimType`) and a range of submission dates "
        + "(`fromDate`, `toDate`); money is reported in the reporting currency, converted at indicative fixed rates.")
@ApiResponse(responseCode = "400", description = "Unknown market or claim type, malformed date, or fromDate after toDate",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Caller is not a MANAGER",
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
                    + "claims, plus the count for every status, per market and per claim type.")
    public StatusSummaryResponse statusSummary(@ParameterObject DashboardCriteria criteria) {
        return dashboardService.statusSummary(criteria.toScope());
    }

    @GetMapping("/staff-workload")
    @Operation(summary = "Claims staff workload",
            description = "Per claims staff member: open claims (under review and awaiting information), decided claims and the "
                    + "exposure of the open ones; plus how many claims still wait in the work queue.")
    public StaffWorkloadResponse staffWorkload(@ParameterObject DashboardCriteria criteria) {
        return dashboardService.staffWorkload(criteria.toScope());
    }

    @GetMapping("/liability-exposure")
    @Operation(summary = "Liability exposure",
            description = "SUM(claimAmount) over every claim whose status is not SETTLED, converted to the reporting currency, "
                    + "with a breakdown by status and, in its own currency too, by market, and by claim type.")
    public LiabilityExposureResponse liabilityExposure(@ParameterObject DashboardCriteria criteria) {
        return dashboardService.liabilityExposure(criteria.toScope());
    }

    @GetMapping("/claims-over-time")
    @Operation(summary = "Claims over time",
            description = "How many claims were submitted in each week or month, and where they stand now (pending or decided, "
                    + "and every status). Weeks for a window of up to 183 days, months beyond; quiet periods appear as zeros. "
                    + "Follows every filter, the market included.")
    public ClaimsOverTimeResponse claimsOverTime(@ParameterObject DashboardCriteria criteria) {
        return dashboardService.claimsOverTime(criteria.toScope());
    }
}
