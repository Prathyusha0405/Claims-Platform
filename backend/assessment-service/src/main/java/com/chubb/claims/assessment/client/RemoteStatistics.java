package com.chubb.claims.assessment.client;

import java.util.List;

/** Claim-service's ClaimStatisticsResponse: raw aggregates the dashboards are derived from. */
public record RemoteStatistics(List<RemoteStatusTotal> byStatus, List<RemoteStaffStatusTotal> byStaff,
                               List<RemoteTypeTotal> byType) {

    public RemoteStatistics {
        byStatus = byStatus == null ? List.of() : byStatus;
        byStaff = byStaff == null ? List.of() : byStaff;
        byType = byType == null ? List.of() : byType;
    }
}
