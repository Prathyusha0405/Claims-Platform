package com.chubb.claims.auth.security;

import com.chubb.claims.auth.config.DemoUserSeeder;
import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end security behaviour against the real filter chain, JWT service, Flyway schema and H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void protectedEndpointWithoutTokenReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void claimantCanRegisterLogInAndReadOwnProfile() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andExpect(jsonPath("$.email").value(email));

        JsonNode login = json(login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600)));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registrationCannotEscalateToAnotherRole() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Mallory","email":"%s","password":"%s","role":"ADMIN"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login(email, "Wrong12345").andExpect(status().isUnauthorized()).andReturn()
                .getResponse().getContentAsString();
        String unknownEmail = login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized()).andReturn()
                .getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("message")).isEqualTo(objectMapper.readTree(unknownEmail).get("message"));
    }

    @Test
    void seededOfficerAndAdminCanLogInWithTheirRoles() throws Exception {
        login(DemoUserSeeder.OFFICER_EMAIL, DemoUserSeeder.OFFICER_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OFFICER"));
        login(DemoUserSeeder.ADMIN_EMAIL, DemoUserSeeder.ADMIN_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void tamperedExpiredForeignAndMalformedTokensAreAllRejected() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        String valid = json(login(email, PASSWORD)).get("accessToken").asText();

        String[] parts = valid.split("\\.");
        char first = parts[1].charAt(5);
        String tamperedPayload = parts[1].substring(0, 5) + (first == 'A' ? 'B' : 'A') + parts[1].substring(6);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        User user = new User("Ghost", "ghost@example.com", "hashed", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 1L);
        String expired = new JwtService(new JwtProperties(jwtSecret, -1_000L)).generateToken(user);
        String foreign = new JwtService(new JwtProperties("another-secret-key-that-is-long-enough-1234", 3_600_000L)).generateToken(user);

        for (String bad : new String[]{tampered, expired, foreign, "not-a-jwt"}) {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void validTokenForADeletedUserIsRejected() throws Exception {
        User ghost = new User("Ghost", "ghost@example.com", "hashed", Role.CLAIMANT);
        ReflectionTestUtils.setField(ghost, "id", 987_654L);
        String token = new JwtService(new JwtProperties(jwtSecret, 3_600_000L)).generateToken(ghost);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenDoesNotBlockPublicEndpoints() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(DemoUserSeeder.OFFICER_EMAIL, DemoUserSeeder.OFFICER_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void springSecuritysAuthenticationManagerChecksPasswordsAgainstTheStoredBcryptHash() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        Authentication ok = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, PASSWORD));

        assertThat(ok.isAuthenticated()).isTrue();
        assertThat(ok.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(ok.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CLAIMANT");
        assertThat(((UserPrincipal) ok.getPrincipal()).getPassword()).startsWith("$2a$");
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdenticallyInsideSpringSecurity() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, "Wrong12345")));
        Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(uniqueEmail(), PASSWORD)));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
    }

    @Test
    void loginIgnoresCaseAndPaddingInTheEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login("   " + email.toUpperCase() + " ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void aVeryLongPasswordIsAnOrdinaryFailedLoginNotAServerError() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email, "a1".repeat(200))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void aPasswordOverBcryptsByteLimitIsRefusedWithAClearMessageNotSilentlyTruncated() throws Exception {
        // 41 characters but 81 bytes: within the 72-character limit yet over bcrypt's 72-byte limit.
        String password = "é".repeat(40) + "1";

        register(uniqueEmail(), password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("must be at most 72 bytes when UTF-8 encoded; use fewer non-ASCII characters"));
    }

    @Test
    void passwordsInAnyScriptWorkEndToEnd() throws Exception {
        String email = uniqueEmail();
        String password = "密码密码密码密码1";

        register(email, password).andExpect(status().isCreated());

        login(email, password).andExpect(status().isOk());
        login(email, "密码密码密码密码2").andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiIsPublicAtTheDocumentedAddress() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiDocumentDescribesTheEndpointsAndMarksTheLoginRoutesPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Auth Service API"))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/users/me'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security.length()").value(0));
    }

    @Test
    void openApiDocumentationIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Jane Tan","email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
