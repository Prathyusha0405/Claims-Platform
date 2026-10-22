package com.chubb.claims.assessment.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET));

    @Test
    void readsTheUserFromATokenInAuthServicesFormat() {
        String token = TestJwt.token(SECRET, 42L, "officer@chubb.com", "Demo Officer", Role.OFFICER, 3_600_000L);

        assertThat(jwtService.parse(token))
                .contains(new AuthenticatedUser(42L, "officer@chubb.com", "Demo Officer", Role.OFFICER));
    }

    @Test
    void authoritiesAreRolePrefixed() {
        AuthenticatedUser admin = jwtService.parse(TestJwt.token(SECRET, 1L, Role.ADMIN)).orElseThrow();

        assertThat(admin.authorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void expiredTokenIsRejected() {
        assertThat(jwtService.parse(TestJwt.token(SECRET, 1L, "a@b.com", "A", Role.OFFICER, -1_000L))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = TestJwt.token("a-completely-different-secret-of-sufficient-length", 1L, Role.ADMIN);

        assertThat(jwtService.parse(foreign)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = TestJwt.token(SECRET, 1L, Role.OFFICER).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes());

        assertThat(jwtService.parse(parts[0] + "." + forged + "." + parts[2])).isEmpty();
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
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new JwtService(new JwtProperties(null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
