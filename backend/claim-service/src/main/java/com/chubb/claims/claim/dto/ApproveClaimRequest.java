package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ApproveClaimRequest(

        @Schema(description = "Optional note recorded in the claim history", example = "Damage confirmed by the repair estimate.")
        @Size(max = 2000)
        String comment) {
}
