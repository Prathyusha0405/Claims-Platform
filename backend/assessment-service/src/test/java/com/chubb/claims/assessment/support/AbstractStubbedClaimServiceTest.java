package com.chubb.claims.assessment.support;

import com.chubb.claims.assessment.security.Role;
import com.chubb.claims.assessment.security.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Base for integration tests that run the whole assessment-service (real filter chain, real database, real HTTP
 * client) against a {@link StubClaimServer} instead of the real claim-service. All subclasses share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractStubbedClaimServiceTest {

    protected static final StubClaimServer CLAIM_SERVICE = StubClaimServer.shared();

    @DynamicPropertySource
    static void pointAtTheStubClaimService(DynamicPropertyRegistry registry) {
        registry.add("app.claim-service.base-url", CLAIM_SERVICE::baseUrl);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    protected String jwtSecret;

    @BeforeEach
    void resetStubClaimService() {
        CLAIM_SERVICE.reset();
    }

    /** The raw JWT (no "Bearer " prefix) for a user with the given id and role. */
    protected String token(long userId, Role role) {
        return TestJwt.token(jwtSecret, userId, role);
    }

    protected String bearer(long userId, Role role) {
        return "Bearer " + token(userId, role);
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** Ids are random so tests sharing one database never collide on claim_id. */
    protected static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
    }
}
