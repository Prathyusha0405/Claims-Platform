package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdditionalInfoRequest(

        @Schema(description = "The information the claims officer asked for",
                example = "Repair estimate from Ace Auto Works attached under reference EST-8841.")
        @NotBlank
        @Size(max = 2000)
        String message) {
}
