package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ApproveClaimRequest(

        @Schema(description = "Optional comment recorded in the claim history and in your review notes",
                example = "Damage confirmed by the repair estimate.")
        @Size(max = 2000)
        String comment) {
}
