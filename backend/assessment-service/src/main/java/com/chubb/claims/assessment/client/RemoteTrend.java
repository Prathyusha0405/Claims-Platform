package com.chubb.claims.assessment.client;

import java.util.List;

/** Claim-service's ClaimTrendResponse: claims submitted per period, ascending and gap-filled; granularity is WEEK or MONTH. */
public record RemoteTrend(String granularity, List<RemoteTrendPeriod> periods) {

    public RemoteTrend {
        periods = periods == null ? List.of() : periods;
    }
}
