package com.chubb.claims.assessment.client;

import com.chubb.claims.assessment.exception.ClaimAccessDeniedException;
import com.chubb.claims.assessment.exception.ClaimConflictException;
import com.chubb.claims.assessment.exception.ClaimNotFoundException;
import com.chubb.claims.assessment.exception.ClaimServiceProtocolException;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.exception.InvalidRequestException;
import com.chubb.claims.assessment.config.ClaimServiceClientConfig;
import com.chubb.claims.assessment.support.ClaimJson;
import com.chubb.claims.assessment.support.StubClaimServer;
import com.chubb.claims.assessment.support.StubClaimServer.StubResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real HTTP client against a real socket: what it sends, what it understands, and how it fails. */
class RestClaimServiceClientTest {

    private static StubClaimServer stub;
    private static RestClaimServiceClient client;

    @BeforeAll
    static void startStub() {
        stub = new StubClaimServer();
        client = clientFor(stub.baseUrl(), Duration.ofSeconds(2));
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @BeforeEach
    void setUp() {
        stub.reset();
        authenticateAs("staff-token");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- what it sends ----------------------------------------------------------------------------------------

    @Test
    void forwardsTheCallersBearerTokenOnEveryRequest() {
        stub.on("POST", "/api/internal/claims/10/assign", 200, ClaimJson.claim(10, "UNDER_REVIEW", 7L));
        stub.on("GET", "/api/internal/claims/statistics", 200, ClaimJson.statistics());

        client.assign(10L);
        client.getStatistics(StatisticsScope.all());

        assertThat(stub.requests()).hasSize(2)
                .allSatisfy(request -> assertThat(request.header("authorization")).isEqualTo("Bearer staff-token"));
    }

    @Test
    void sendsNoCredentialsWhenThereIsNoAuthenticatedCaller() {
        SecurityContextHolder.clearContext();
        stub.on("GET", "/api/internal/claims/10", 200, ClaimJson.claim(10, "SUBMITTED", null));

        client.getClaim(10L);

        assertThat(stub.lastRequest().header("authorization")).isNull();
    }

    @Test
    void decisionsAreSentAsJsonBodies() {
        stub.on("POST", "/api/internal/claims/10/approve", 200, ClaimJson.claim(10, "APPROVED", 7L));
        stub.on("POST", "/api/internal/claims/10/reject", 200, ClaimJson.claim(10, "REJECTED", 7L));
        stub.on("POST", "/api/internal/claims/10/request-info", 200, ClaimJson.claim(10, "MORE_INFO_REQUIRED", 7L));

        client.approve(10L, "Looks good");
        client.reject(10L, "Policy lapsed");
        client.requestInfo(10L, "Send photos");

        assertThat(stub.requests()).extracting(StubClaimServer.RecordedRequest::body)
                .containsExactly("{\"comment\":\"Looks good\"}", "{\"reason\":\"Policy lapsed\"}", "{\"message\":\"Send photos\"}");
        assertThat(stub.requests()).allSatisfy(request -> assertThat(request.header("content-type")).startsWith("application/json"));
    }

    @Test
    void settlingPostsTheCommentToTheSettleEndpointAndANullCommentWhenThereIsNone() {
        stub.on("POST", "/api/internal/claims/10/settle", 200, ClaimJson.claim(10, "SETTLED", 7L));

        RemoteClaim settled = client.settle(10L, "Paid in full");
        assertThat(settled.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(stub.lastRequest().body()).isEqualTo("{\"comment\":\"Paid in full\"}");

        client.settle(10L, null);
        assertThat(stub.lastRequest().body()).isEqualTo("{\"comment\":null}");
    }

    @Test
    void approvingWithoutACommentSendsANullComment() {
        stub.on("POST", "/api/internal/claims/10/approve", 200, ClaimJson.claim(10, "APPROVED", 7L));

        client.approve(10L, null);

        assertThat(stub.lastRequest().body()).isEqualTo("{\"comment\":null}");
    }

    @Test
    void workQueueRequestsSubmittedClaimsWithPagingAndSorting() {
        stub.on("GET", "/api/claims/search", 200, ClaimJson.page(ClaimJson.claim(10, "SUBMITTED", null)));

        client.findUnassigned(null, PageRequest.of(2, 5, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.DESC, "claimAmount"))));

        assertThat(stub.lastRequest().query()).isEqualTo("status=SUBMITTED&page=2&size=5&sort=createdAt,asc&sort=claimAmount,desc");
    }

    @Test
    void workQueueCanBeNarrowedToOneMarket() {
        stub.on("GET", "/api/claims/search", 200, ClaimJson.page(ClaimJson.claim(10, "SUBMITTED", null)));

        client.findUnassigned(" NZ ", PageRequest.of(0, 20));

        assertThat(stub.lastRequest().query()).isEqualTo("status=SUBMITTED&page=0&size=20&market=NZ");
    }

    @Test
    void aBlankMarketMeansEveryMarket() {
        stub.on("GET", "/api/claims/search", 200, ClaimJson.page(ClaimJson.claim(10, "SUBMITTED", null)));

        client.findUnassigned("  ", PageRequest.of(0, 20));

        assertThat(stub.lastRequest().query()).isEqualTo("status=SUBMITTED&page=0&size=20");
    }

    // ---- what it understands ----------------------------------------------------------------------------------

    @Test
    void mapsAClaimFromClaimServicesJson() {
        stub.on("GET", "/api/internal/claims/10", 200, ClaimJson.claim(10, "UNDER_REVIEW", 7L, "2500.50"));

        RemoteClaim claim = client.getClaim(10L);

        assertThat(claim).isEqualTo(new RemoteClaim(10L, "CLM-2026-000010", "POL-2026-1", "AUTO", "SG", ClaimStatus.UNDER_REVIEW,
                new BigDecimal("2500.50"), "SGD", LocalDate.of(2026, 10, 1), 7L,
                Instant.parse("2026-10-22T06:00:00Z"), Instant.parse("2026-10-22T06:30:00Z")));
    }

    @Test
    void mapsAPageOfClaims() {
        stub.on("GET", "/api/claims/search", 200, ClaimJson.page(ClaimJson.claim(10, "SUBMITTED", null), ClaimJson.claim(11, "SUBMITTED", null)));

        RemotePage<RemoteClaim> page = client.findUnassigned(null, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(RemoteClaim::id).containsExactly(10L, 11L);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    void mapsStatistics() {
        stub.on("GET", "/api/internal/claims/statistics", 200, ClaimJson.statistics());

        RemoteStatistics statistics = client.getStatistics(StatisticsScope.all());

        assertThat(statistics.byStatus()).hasSize(8);
        assertThat(statistics.byStatus().get(0))
                .isEqualTo(new RemoteStatusTotal("SG", "SGD", ClaimStatus.SUBMITTED, 2, new BigDecimal("270.00")));
        assertThat(statistics.byStatus().get(1))
                .isEqualTo(new RemoteStatusTotal("JP", "JPY", ClaimStatus.SUBMITTED, 1, new BigDecimal("15000.00")));
        assertThat(statistics.byStaff()).hasSize(6);
        assertThat(statistics.byStaff().get(2))
                .isEqualTo(new RemoteStaffStatusTotal(8L, "JP", "JPY", ClaimStatus.MORE_INFO_REQUIRED, 1, new BigDecimal("75075.00")));
        assertThat(statistics.byType()).hasSize(11);
        assertThat(statistics.byType().get(1))
                .isEqualTo(new RemoteTypeTotal("SG", "SGD", ClaimType.HOME, ClaimStatus.SUBMITTED, 1, new BigDecimal("135.00")));
        assertThat(statistics.byType().get(10))
                .isEqualTo(new RemoteTypeTotal("JP", "JPY", ClaimType.HOME, ClaimStatus.REJECTED, 1, new BigDecimal("15000.00")));
    }

    @Test
    void treatsMissingStatisticsListsAsEmpty() {
        stub.on("GET", "/api/internal/claims/statistics", 200, "{}");

        RemoteStatistics statistics = client.getStatistics(StatisticsScope.all());

        assertThat(statistics.byStatus()).isEmpty();
        assertThat(statistics.byStaff()).isEmpty();
        assertThat(statistics.byType()).isEmpty();
    }

    @Test
    void anUnfilteredStatisticsRequestHasNoQueryString() {
        stub.on("GET", "/api/internal/claims/statistics", 200, ClaimJson.statistics());

        client.getStatistics(StatisticsScope.all());

        assertThat(stub.lastRequest().query()).isNull();
    }

    @Test
    void statisticsAreNarrowedByTypeAndDatesButNeverByMarket() {
        stub.on("GET", "/api/internal/claims/statistics", 200, ClaimJson.statistics());

        client.getStatistics(new StatisticsScope(Market.JP, ClaimType.HOME, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)));

        // The market is applied by the dashboard itself so that the per-market comparison keeps seeing every market.
        assertThat(stub.lastRequest().query()).contains("claimType=HOME", "fromDate=2026-08-01", "toDate=2026-10-31")
                .doesNotContain("market");
    }

    @Test
    void theTrendIsNarrowedByEveryFilterIncludingTheMarket() {
        stub.on("GET", "/api/internal/claims/trend", 200, ClaimJson.trend());

        RemoteTrend trend = client.getTrend(new StatisticsScope(Market.JP, ClaimType.AUTO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)));

        assertThat(stub.lastRequest().query()).contains("market=JP", "claimType=AUTO", "fromDate=2026-08-01", "toDate=2026-10-31");
        assertThat(trend.granularity()).isEqualTo("WEEK");
        assertThat(trend.periods()).hasSize(2);
        assertThat(trend.periods().get(0).start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(trend.periods().get(0).claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 2L);
        assertThat(trend.periods().get(1).claimsByStatus()).containsEntry(ClaimStatus.MORE_INFO_REQUIRED, 1L);
    }

    @Test
    void anUnfilteredTrendHasNoQueryStringAndMissingPeriodsAreEmpty() {
        stub.on("GET", "/api/internal/claims/trend", 200, "{\"granularity\":\"WEEK\"}");

        RemoteTrend trend = client.getTrend(StatisticsScope.all());

        assertThat(stub.lastRequest().query()).isNull();
        assertThat(trend.periods()).isEmpty();
    }

    // ---- how it fails -----------------------------------------------------------------------------------------

    static Stream<Arguments> errorTranslations() {
        return Stream.of(
                Arguments.of(400, ClaimJson.error(400, "Unsupported sort field 'x'"), InvalidRequestException.class, "Unsupported sort field 'x'"),
                Arguments.of(403, ClaimJson.error(403, "This claim is not assigned to you"), ClaimAccessDeniedException.class, "This claim is not assigned to you"),
                Arguments.of(404, ClaimJson.error(404, "Claim '10' was not found"), ClaimNotFoundException.class, "Claim '10' was not found"),
                Arguments.of(409, ClaimJson.error(409, "Only a SUBMITTED claim can be assigned"), ClaimConflictException.class, "Only a SUBMITTED claim can be assigned"),
                Arguments.of(401, ClaimJson.error(401, "Authentication is required"), ClaimServiceProtocolException.class, "did not accept the caller's credentials"),
                Arguments.of(500, ClaimJson.error(500, "boom"), ClaimServiceUnavailableException.class, "failed with HTTP 500"),
                Arguments.of(503, "", ClaimServiceUnavailableException.class, "failed with HTTP 503"),
                Arguments.of(418, ClaimJson.error(418, "teapot"), ClaimServiceProtocolException.class, "unexpected HTTP 418"));
    }

    @ParameterizedTest(name = "HTTP {0} -> {2}")
    @MethodSource("errorTranslations")
    void translatesClaimServiceErrors(int status, String body, Class<? extends RuntimeException> expected, String messagePart) {
        stub.on("POST", "/api/internal/claims/10/assign", status, body);

        assertThatThrownBy(() -> client.assign(10L)).isInstanceOf(expected).hasMessageContaining(messagePart);
    }

    @Test
    void translatesErrorsOnReadsToo() {
        stub.on("GET", "/api/internal/claims/10", 404, ClaimJson.error(404, "Claim '10' was not found"));
        stub.on("GET", "/api/internal/claims/statistics", 403, ClaimJson.error(403, "You do not have permission"));
        stub.on("GET", "/api/internal/claims/trend", 403, ClaimJson.error(403, "You do not have permission"));
        stub.on("GET", "/api/claims/search", 400, ClaimJson.error(400, "Unsupported sort field 'x'"));

        assertThatThrownBy(() -> client.getClaim(10L)).isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> client.getStatistics(StatisticsScope.all())).isInstanceOf(ClaimAccessDeniedException.class);
        assertThatThrownBy(() -> client.getTrend(StatisticsScope.all())).isInstanceOf(ClaimAccessDeniedException.class);
        assertThatThrownBy(() -> client.findUnassigned(null, PageRequest.of(0, 20))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void usesAGenericMessageWhenTheErrorBodyIsNotJson() {
        stub.on("POST", "/api/internal/claims/10/assign", 409, "<html>conflict</html>");

        assertThatThrownBy(() -> client.assign(10L))
                .isInstanceOf(ClaimConflictException.class)
                .hasMessage("Claim service answered HTTP 409");
    }

    @Test
    void reportsClaimServiceAsUnavailableWhenNothingIsListening() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClaimServiceClient unreachable = clientFor("http://127.0.0.1:" + closedPort, Duration.ofSeconds(1));

        assertThatThrownBy(() -> unreachable.getClaim(10L))
                .isInstanceOf(ClaimServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void reportsClaimServiceAsUnavailableWhenItAnswersTooSlowly() {
        stub.on("GET", "/api/internal/claims/10", request ->
                new StubResponse(200, ClaimJson.claim(10, "SUBMITTED", null), Duration.ofMillis(1500)));
        RestClaimServiceClient impatient = clientFor(stub.baseUrl(), Duration.ofMillis(300));

        assertThatThrownBy(() -> impatient.getClaim(10L)).isInstanceOf(ClaimServiceUnavailableException.class);
    }

    @Test
    void anUnreadableSuccessBodyIsAProtocolError() {
        stub.on("GET", "/api/internal/claims/10", 200, "{ this is not json");

        assertThatThrownBy(() -> client.getClaim(10L)).isInstanceOf(ClaimServiceProtocolException.class);
    }

    @Test
    void anEmptySuccessBodyIsAProtocolError() {
        stub.on("POST", "/api/internal/claims/10/assign", 200, "");

        assertThatThrownBy(() -> client.assign(10L))
                .isInstanceOf(ClaimServiceProtocolException.class)
                .hasMessageContaining("empty response");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static void authenticateAs(String token) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("staff", token, List.of()));
    }

    private static RestClaimServiceClient clientFor(String baseUrl, Duration readTimeout) {
        ClaimServiceProperties properties = new ClaimServiceProperties(baseUrl, Duration.ofSeconds(1), readTimeout);
        return new RestClaimServiceClient(ClaimServiceClientConfig.restClient(RestClient.builder(), properties), new ObjectMapper());
    }
}
