package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.entity.ClaimHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, Long> {

    /** Oldest first; id breaks ties between events recorded in the same instant. */
    List<ClaimHistory> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);

    /** Whether the event with this id has already been recorded (see {@code ClaimEvent.id()}). */
    boolean existsByEventId(String eventId);

    /** Each user who has performed an event under the given role, once, in ascending id order. */
    @Query("select distinct h.performedBy from ClaimHistory h "
            + "where h.performedByRole = :role and h.performedBy is not null order by h.performedBy")
    List<Long> findActorIdsByRole(@Param("role") String role);
}
