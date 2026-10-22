package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.entity.ClaimHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, Long> {

    /** Oldest first; id breaks ties between events recorded in the same instant. */
    List<ClaimHistory> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
