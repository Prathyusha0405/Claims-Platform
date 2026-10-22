package com.chubb.claims.auth.seed;

import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import com.chubb.claims.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full migration set (V1 schema + V2 demo data) on a private database. Pins the user seed to what the other two
 * services' seeds assume: ids 1 (admin), 2-6 (officers) and 7-26 (claimants).
 */
@SpringBootTest(properties = {
        "spring.flyway.target=latest",
        "spring.datasource.url=jdbc:h2:mem:authseedcheck;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SeedDataIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void thereIsOneAdminFiveOfficersAndTwentyClaimants() {
        Map<String, Long> byRole = new java.util.TreeMap<>();
        jdbc.queryForList("SELECT role, COUNT(*) AS n FROM users GROUP BY role")
                .forEach(row -> byRole.put((String) row.get("ROLE"), ((Number) row.get("N")).longValue()));

        assertThat(byRole).containsExactly(Map.entry("ADMIN", 1L), Map.entry("CLAIMANT", 20L), Map.entry("OFFICER", 5L));
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(26);
    }

    @Test
    void idsFollowTheContractTheOtherServicesSeedsRelyOn() {
        assertThat(ids("ADMIN")).containsExactly(1L);
        assertThat(ids("OFFICER")).containsExactly(2L, 3L, 4L, 5L, 6L);
        assertThat(ids("CLAIMANT")).containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(7, 26).boxed().toList());
    }

    @Test
    void theDocumentedDemoAccountsExist() {
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'admin@chubb.com'", String.class)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT id FROM users WHERE email = 'officer@chubb.com'", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE email = 'weiling.tan@example.com'", String.class)).isEqualTo("Wei Ling Tan");
    }

    @Test
    void staffUseCompanyAddressesAndClaimantsDoNot() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE role <> 'CLAIMANT' AND email NOT LIKE '%@chubb.com'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'CLAIMANT' AND email NOT LIKE '%@example.com'")).isZero();
    }

    @Test
    void everyPasswordHashMatchesTheDocumentedDemoPassword() {
        jdbc.queryForList("SELECT email, role, password_hash FROM users").forEach(row -> {
            String expected = switch ((String) row.get("ROLE")) {
                case "ADMIN" -> "Admin@123";
                case "OFFICER" -> "Officer@123";
                default -> "Claimant@123";
            };
            assertThat(passwordEncoder.matches(expected, (String) row.get("PASSWORD_HASH")))
                    .as("password for %s", row.get("EMAIL")).isTrue();
            assertThat((String) row.get("PASSWORD_HASH")).startsWith("$2a$10$").hasSize(60);
        });
    }

    @Test
    void auditColumnsAreSaneAndEveryAccountPredatesTheAnchorDate() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE updated_at < created_at")).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE created_at > TIMESTAMP WITH TIME ZONE '2026-10-22 06:00:00+00:00'")).isZero();
    }

    @Test
    void theApplicationCanReadEverySeededRow() {
        List<User> users = userRepository.findAll();

        assertThat(users).hasSize(26);
        assertThat(users).extracting(User::getRole).contains(Role.ADMIN, Role.OFFICER, Role.CLAIMANT);
        assertThat(users).allSatisfy(user -> {
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    @Transactional
    void theIdentityCounterWasMovedPastTheSeededIds() {
        User next = userRepository.saveAndFlush(new User("New Person", "new.person@example.com", "hash", Role.CLAIMANT));

        assertThat(next.getId()).isEqualTo(27L);
    }

    @Test
    void seededAccountsCanLogInAndSeeTheirOwnProfile() throws Exception {
        assertLogin("admin@chubb.com", "Admin@123", "ADMIN");
        assertLogin("officer@chubb.com", "Officer@123", "OFFICER");
        assertLogin("kenji.sato@chubb.com", "Officer@123", "OFFICER");
        String token = assertLogin("hiroshi.tanaka@example.com", "Claimant@123", "CLAIMANT");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Hiroshi Tanaka"))
                .andExpect(jsonPath("$.role").value("CLAIMANT"));
    }

    @Test
    void aWrongPasswordStillFails() throws Exception {
        login("admin@chubb.com", "Officer@123").andExpect(status().isUnauthorized());
    }

    private String assertLogin(String email, String password, String expectedRole) throws Exception {
        ResultActions result = login(email, password).andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(expectedRole));
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private List<Long> ids(String role) {
        return jdbc.queryForList("SELECT id FROM users WHERE role = ? ORDER BY id", Long.class, role);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
