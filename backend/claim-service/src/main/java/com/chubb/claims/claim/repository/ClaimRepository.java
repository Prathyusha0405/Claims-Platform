package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.StaffStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.dto.TrendRow;
import com.chubb.claims.claim.dto.TypeStatusTotal;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    /** The time window every aggregate honours; a market or type filter is added on top where a query takes one. */
    String WITHIN_WINDOW = "c.createdAt >= :from and c.createdAt < :to";
    String OF_TYPE = " and (:anyType = true or c.claimType = :type)";
    String IN_MARKET = " and (:anyMarket = true or c.market = :market)";

    Optional<Claim> findByClaimNumber(String claimNumber);

    Page<Claim> findByClaimantId(Long claimantId, Pageable pageable);

    /** Claims per market and status in the scope's window and type; the scope's market is not used here. */
    default List<StatusTotal> totalsByStatus(ClaimScope scope) {
        return totalsByStatus(scope.from(), scope.toExclusive(), scope.anyType(), scope.typeOrPlaceholder());
    }

    default List<StaffStatusTotal> totalsByStaffAndStatus(ClaimScope scope) {
        return totalsByStaffAndStatus(scope.from(), scope.toExclusive(), scope.anyType(), scope.typeOrPlaceholder());
    }

    /** Follows the window but never the scope's type, so one type can be read against the others. */
    default List<TypeStatusTotal> totalsByTypeAndStatus(ClaimScope scope) {
        return totalsByTypeAndStatus(scope.from(), scope.toExclusive());
    }

    /** One row per claim in the scope, reduced to submission time and status, for bucketing into periods. */
    default List<TrendRow> findTrendRows(ClaimScope scope) {
        return findTrendRows(scope.from(), scope.toExclusive(), scope.anyMarket(), scope.marketOrPlaceholder(),
                scope.anyType(), scope.typeOrPlaceholder());
    }

    @Query("select new com.chubb.claims.claim.dto.StatusTotal(c.market, c.currency, c.status, count(c), sum(c.claimAmount)) "
            + "from Claim c where " + WITHIN_WINDOW + OF_TYPE + " group by c.market, c.currency, c.status")
    List<StatusTotal> totalsByStatus(@Param("from") Instant from, @Param("to") Instant to,
                                     @Param("anyType") boolean anyType, @Param("type") ClaimType type);

    @Query("select new com.chubb.claims.claim.dto.StaffStatusTotal(c.assignedStaffId, c.market, c.currency, c.status, "
            + "count(c), sum(c.claimAmount)) "
            + "from Claim c where c.assignedStaffId is not null and " + WITHIN_WINDOW + OF_TYPE
            + " group by c.assignedStaffId, c.market, c.currency, c.status")
    List<StaffStatusTotal> totalsByStaffAndStatus(@Param("from") Instant from, @Param("to") Instant to,
                                                  @Param("anyType") boolean anyType, @Param("type") ClaimType type);

    @Query("select new com.chubb.claims.claim.dto.TypeStatusTotal(c.market, c.currency, c.claimType, c.status, count(c), "
            + "sum(c.claimAmount)) from Claim c where " + WITHIN_WINDOW
            + " group by c.market, c.currency, c.claimType, c.status")
    List<TypeStatusTotal> totalsByTypeAndStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select new com.chubb.claims.claim.dto.TrendRow(c.createdAt, c.status) "
            + "from Claim c where " + WITHIN_WINDOW + IN_MARKET + OF_TYPE)
    List<TrendRow> findTrendRows(@Param("from") Instant from, @Param("to") Instant to,
                                 @Param("anyMarket") boolean anyMarket, @Param("market") Market market,
                                 @Param("anyType") boolean anyType, @Param("type") ClaimType type);
}
