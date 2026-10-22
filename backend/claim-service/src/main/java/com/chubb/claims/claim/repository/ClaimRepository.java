package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.OfficerStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.Claim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    Page<Claim> findByClaimantId(Long claimantId, Pageable pageable);

    @Query("select new com.chubb.claims.claim.dto.StatusTotal(c.status, count(c), sum(c.claimAmount)) "
            + "from Claim c group by c.status")
    List<StatusTotal> totalsByStatus();

    @Query("select new com.chubb.claims.claim.dto.OfficerStatusTotal(c.assignedOfficerId, c.status, count(c), sum(c.claimAmount)) "
            + "from Claim c where c.assignedOfficerId is not null group by c.assignedOfficerId, c.status")
    List<OfficerStatusTotal> totalsByOfficerAndStatus();
}
