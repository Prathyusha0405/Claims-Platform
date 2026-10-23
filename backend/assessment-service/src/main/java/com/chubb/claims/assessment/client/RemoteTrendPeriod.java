package com.chubb.claims.assessment.client;

import java.time.LocalDate;
import java.util.Map;

/** Claim-service's TrendPeriod: the claims submitted in one week or month, by the status they have reached now. */
public record RemoteTrendPeriod(LocalDate start, Map<ClaimStatus, Long> claimsByStatus) {

    public RemoteTrendPeriod {
        claimsByStatus = claimsByStatus == null ? Map.of() : claimsByStatus;
    }
}
