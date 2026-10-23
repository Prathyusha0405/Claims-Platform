package com.chubb.claims.assessment.repository;

import com.chubb.claims.assessment.entity.ClaimAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClaimAssessmentRepository extends JpaRepository<ClaimAssessment, Long> {

    Optional<ClaimAssessment> findByClaimId(Long claimId);

    /** One display name per staff member who has ever taken a claim; claim-service only knows staff member ids. */
    @Query("select a.staffId as staffId, max(a.staffName) as staffName from ClaimAssessment a group by a.staffId")
    List<StaffName> findStaffNames();

    interface StaffName {

        Long getStaffId();

        String getStaffName();
    }
}
