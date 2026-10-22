package com.chubb.claims.claim.dto;

import com.chubb.claims.claim.entity.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(

        @Schema(description = "Policy the claim is made against", example = "POL-2026-12345")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9/-]{3,29}$",
                message = "must be 4-30 characters: letters, digits, '-' or '/'")
        String policyNumber,

        @Schema(example = "AUTO")
        @NotNull
        ClaimType claimType,

        @Schema(description = "Date the incident happened (not in the future)", example = "2026-10-15")
        @NotNull
        LocalDate incidentDate,

        @Schema(example = "Orchard Road, Singapore")
        @Size(max = 200)
        String incidentLocation,

        @Schema(example = "Rear-ended at a traffic light; bumper and tail light damaged.")
        @NotBlank
        @Size(max = 2000)
        String description,

        @Schema(description = "Amount claimed, up to 2 decimal places", example = "2500.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "must have at most 13 integer digits and 2 decimal places")
        BigDecimal claimAmount) {
}
