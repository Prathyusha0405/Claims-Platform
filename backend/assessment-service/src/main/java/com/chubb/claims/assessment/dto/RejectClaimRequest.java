package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectClaimRequest(

        @Schema(description = "Why the claim is rejected. Required; the claimant can see it in the claim history.",
                example = "Policy had lapsed on the incident date.")
        @NotBlank
        @Size(max = 2000)
        String reason) {
}
