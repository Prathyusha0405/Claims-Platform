package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Raw aggregates over the claims in scope (all of them unless date or type filters were given), for assessment-service's
 * dashboards. Only combinations that have claims appear. Amounts are in each row's own currency and are never summed across
 * currencies here: the caller converts, because the rates belong to the dashboard and not to the claims.
 * Business metrics (pending, liability exposure, ...) are derived from these by the caller.
 */
public record ClaimStatisticsResponse(
        @Schema(description = "One entry per market and status that has at least one claim")
        List<StatusTotal> byStatus,
        @Schema(description = "One entry per staff member, market and status, for assigned claims only")
        List<StaffStatusTotal> byStaff,
        @Schema(description = "One entry per market, claim type and status that has at least one claim. Follows the date "
                + "filter but never the claim type filter, so one type can be read against the others")
        List<TypeStatusTotal> byType) {
}
