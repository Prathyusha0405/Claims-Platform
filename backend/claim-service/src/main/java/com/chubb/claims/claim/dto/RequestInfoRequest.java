package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestInfoRequest(

        @Schema(description = "What the claimant must provide; recorded in the claim history",
                example = "Please send a repair estimate and photos of the damage.")
        @NotBlank
        @Size(max = 2000)
        String message) {
}
