package com.chubb.claims.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddNoteRequest(

        @Schema(description = "Internal note; never shown to the claimant", example = "Called the workshop; estimate looks inflated.")
        @NotBlank
        @Size(max = 2000)
        String content) {
}
