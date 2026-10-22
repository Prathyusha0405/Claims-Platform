package com.chubb.claims.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(

        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "3600")
        long expiresIn,

        UserResponse user) {

    public static final String BEARER = "Bearer";

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, BEARER, expiresInSeconds, user);
    }
}
