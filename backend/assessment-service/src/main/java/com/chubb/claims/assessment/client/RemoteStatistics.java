package com.chubb.claims.assessment.client;

import java.util.List;

/** Claim-service's ClaimStatisticsResponse: raw aggregates the dashboards are derived from. */
public record RemoteStatistics(List<RemoteStatusTotal> byStatus, List<RemoteOfficerStatusTotal> byOfficer) {

    public RemoteStatistics {
        byStatus = byStatus == null ? List.of() : byStatus;
        byOfficer = byOfficer == null ? List.of() : byOfficer;
    }
}
