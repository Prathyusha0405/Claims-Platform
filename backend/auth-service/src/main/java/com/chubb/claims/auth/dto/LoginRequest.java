package com.chubb.claims.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(example = "jane.tan@example.com")
        @NotBlank
        String email,

        @Schema(example = "Claims#2026")
        @NotBlank
        String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
