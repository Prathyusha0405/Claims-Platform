package com.chubb.claims.assessment.repository;

import com.chubb.claims.assessment.entity.ClaimNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimNoteRepository extends JpaRepository<ClaimNote, Long> {

    /** Oldest first; id breaks ties between notes written in the same instant. */
    List<ClaimNote> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
