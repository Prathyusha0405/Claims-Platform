package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Raw aggregates over all claims, for assessment-service's dashboards. Only statuses that have claims appear.
 * Business metrics (pending, liability exposure, ...) are derived from these by the caller.
 */
public record ClaimStatisticsResponse(
        @Schema(description = "One entry per status that has at least one claim")
        List<StatusTotal> byStatus,
        @Schema(description = "One entry per officer and status, for assigned claims only")
        List<OfficerStatusTotal> byOfficer) {
}
