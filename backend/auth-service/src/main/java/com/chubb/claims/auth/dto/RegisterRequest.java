package com.chubb.claims.auth.dto;

import com.chubb.claims.auth.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(example = "Jane Tan")
        @NotBlank
        @Size(max = 100)
        String fullName,

        @Schema(example = "jane.tan@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "Claims#2026",
                description = "8-72 characters (at most 72 bytes in UTF-8) with at least one letter and one digit; "
                        + "letters and digits of any script are accepted")
        @NotBlank
        @Size(min = 8, max = 72)
        @MaxUtf8Bytes(72)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\p{Nd}).+$", message = "must contain at least one letter and one digit")
        String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=***]";
    }
}
