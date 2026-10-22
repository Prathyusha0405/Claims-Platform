package com.chubb.claims.assessment.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Builds tokens shaped exactly like the ones auth-service issues, for tests. */
public final class TestJwt {

    private TestJwt() {
    }

    public static String token(String secret, long userId, String email, String fullName, Role role, long ttlMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("name", fullName)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    public static String token(String secret, long userId, Role role) {
        return token(secret, userId, "user" + userId + "@example.com", "User " + userId, role, 3_600_000L);
    }
}
