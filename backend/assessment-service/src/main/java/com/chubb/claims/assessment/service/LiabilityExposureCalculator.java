package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.client.RemoteStatusTotal;
import com.chubb.claims.assessment.dto.LiabilityExposureResponse;
import com.chubb.claims.assessment.dto.StatusExposure;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Liability exposure: {@code SUM(claimAmount) WHERE status != SETTLED}.
 * <p>
 * Claim-service does the heavy lifting (one GROUP BY over its table) and hands over per-status totals; the business
 * rule itself lives here, in one place, as a pure function of those totals.
 */
@Component
public class LiabilityExposureCalculator {

    public static final String FORMULA = "SUM(claimAmount) WHERE status != SETTLED";

    private static final int MONEY_SCALE = 2;

    public LiabilityExposureResponse calculate(List<RemoteStatusTotal> totals, Instant generatedAt) {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        Map<ClaimStatus, BigDecimal> amounts = new EnumMap<>(ClaimStatus.class);
        for (RemoteStatusTotal total : totals) {
            counts.merge(total.status(), total.claimCount(), Long::sum);
            amounts.merge(total.status(), total.totalAmount() == null ? BigDecimal.ZERO : total.totalAmount(), BigDecimal::add);
        }

        BigDecimal exposure = zero();
        BigDecimal excluded = zero();
        long exposedClaims = 0;
        List<StatusExposure> byStatus = new ArrayList<>();

        for (ClaimStatus status : ClaimStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            BigDecimal amount = amounts.getOrDefault(status, BigDecimal.ZERO).setScale(MONEY_SCALE);
            boolean included = status.countsTowardLiability();

            if (included) {
                exposure = exposure.add(amount);
                exposedClaims += count;
            } else {
                excluded = excluded.add(amount);
            }
            byStatus.add(new StatusExposure(status, count, amount, included));
        }

        return new LiabilityExposureResponse(exposure, exposedClaims, excluded, FORMULA, List.copyOf(byStatus), generatedAt);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
