package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ClaimTrendResponse;
import com.chubb.claims.claim.dto.TrendCriteria;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.ClaimScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How many claims were submitted in each week or month, and where they stand now. The claims are chosen in the database
 * (window, market and type); only the cheap step of putting each one into its period happens here, in {@link TrendBuilder}.
 * Each claim is read as two values, so the cost grows with the claims in scope; a database-side group-by or a daily rollup
 * is the move if that ever matters.
 */
@Service
public class ClaimTrendService {

    private final ClaimRepository claimRepository;

    public ClaimTrendService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Transactional(readOnly = true)
    public ClaimTrendResponse trend(TrendCriteria criteria) {
        ClaimScope scope = ClaimScope.of(criteria);
        return TrendBuilder.build(claimRepository.findTrendRows(scope), criteria.fromDate(), criteria.toDate());
    }
}
