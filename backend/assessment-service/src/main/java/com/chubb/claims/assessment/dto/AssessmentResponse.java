package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.ClaimStatus;
import com.chubb.claims.assessment.entity.AssessmentDecision;
import com.chubb.claims.assessment.entity.ClaimAssessment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The officer's assessment of a claim, together with the claim's status right after the action. */
public record AssessmentResponse(
        Long claimId,
        String claimNumber,
        @Schema(description = "Claim status as reported by claim-service after this action")
        ClaimStatus claimStatus,
        Long officerId,
        String officerName,
        Instant assignedAt,
        @Schema(description = "Set once the officer approves or rejects; null while the claim is being reviewed")
        AssessmentDecision decision,
        String decisionReason,
        Instant decidedAt) {

    public static AssessmentResponse from(ClaimAssessment assessment, ClaimStatus claimStatus) {
        return new AssessmentResponse(
                assessment.getClaimId(),
                assessment.getClaimNumber(),
                claimStatus,
                assessment.getOfficerId(),
                assessment.getOfficerName(),
                assessment.getAssignedAt(),
                assessment.getDecision(),
                assessment.getDecisionReason(),
                assessment.getDecidedAt());
    }
}
