package com.chubb.claims.auth.security;

import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, ONE_HOUR_MS));

    @Test
    void tokenRoundTripsUserIdEmailAndRole() {
        String token = jwtService.generateToken(user(42L, Role.OFFICER));

        Optional<AuthenticatedUser> parsed = jwtService.parse(token);

        assertThat(parsed).contains(new AuthenticatedUser(42L, "officer@chubb.com", Role.OFFICER));
    }

    @Test
    void authoritiesAreRolePrefixed() {
        AuthenticatedUser admin = jwtService.parse(jwtService.generateToken(user(1L, Role.ADMIN))).orElseThrow();

        assertThat(admin.authorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void expiresInSecondsReflectsConfiguredLifetime() {
        assertThat(jwtService.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService alreadyExpired = new JwtService(new JwtProperties(SECRET, -1_000L));

        assertThat(jwtService.parse(alreadyExpired.generateToken(user(1L, Role.CLAIMANT)))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(new JwtProperties("a-completely-different-secret-of-sufficient-length", ONE_HOUR_MS));

        assertThat(jwtService.parse(other.generateToken(user(1L, Role.ADMIN)))).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtService.generateToken(user(1L, Role.CLAIMANT)).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"email\":\"jane@example.com\",\"role\":\"ADMIN\"}".getBytes());

        assertThat(jwtService.parse(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes());

        assertThat(jwtService.parse(header + "." + payload + ".")).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.parse("not-a-jwt")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    @Test
    void secretShorterThan256BitsFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", ONE_HOUR_MS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(null, ONE_HOUR_MS)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static User user(Long id, Role role) {
        User user = new User("Test User", role == Role.OFFICER ? "officer@chubb.com" : "jane@example.com", "hashed", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
