package com.chubb.claims.assessment.repository;

import com.chubb.claims.assessment.entity.ClaimAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClaimAssessmentRepository extends JpaRepository<ClaimAssessment, Long> {

    Optional<ClaimAssessment> findByClaimId(Long claimId);

    /** One display name per officer who has ever taken a claim; claim-service only knows officer ids. */
    @Query("select a.officerId as officerId, max(a.officerName) as officerName from ClaimAssessment a group by a.officerId")
    List<OfficerName> findOfficerNames();

    interface OfficerName {

        Long getOfficerId();

        String getOfficerName();
    }
}
