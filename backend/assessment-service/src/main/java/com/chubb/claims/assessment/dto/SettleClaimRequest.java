package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record SettleClaimRequest(

        @Schema(description = "Optional comment recorded in the claim history, which the claimant can read",
                example = "Repair invoice paid on 2026-10-26.")
        @Size(max = 2000)
        String comment) {
}
