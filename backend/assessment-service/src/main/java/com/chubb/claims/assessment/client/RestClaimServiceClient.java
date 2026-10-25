package com.chubb.claims.assessment.client;

import com.chubb.claims.assessment.exception.ClaimAccessDeniedException;
import com.chubb.claims.assessment.exception.ClaimConflictException;
import com.chubb.claims.assessment.exception.ClaimNotFoundException;
import com.chubb.claims.assessment.exception.ClaimServiceProtocolException;
import com.chubb.claims.assessment.exception.ClaimServiceUnavailableException;
import com.chubb.claims.assessment.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * HTTP implementation of {@link ClaimServiceClient}. Claim-service's error answers are translated into this
 * service's exceptions, keeping claim-service's own message, so a staff member sees "This claim is not assigned to you"
 * rather than a generic failure.
 */
@Component
public class RestClaimServiceClient implements ClaimServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RestClaimServiceClient.class);

    private static final String CLAIMS_API = "/api/internal/claims";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClaimServiceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RemotePage<RemoteClaim> findUnassigned(String market, Pageable pageable) {
        return call(() -> restClient.get()
                .uri(uri -> {
                    uri.path("/api/claims/search")
                            .queryParam("status", ClaimStatus.SUBMITTED)
                            .queryParam("page", pageable.getPageNumber())
                            .queryParam("size", pageable.getPageSize());
                    if (market != null && !market.isBlank()) {
                        uri.queryParam("market", market.trim());
                    }
                    pageable.getSort().forEach(order -> uri.queryParam("sort",
                            order.getProperty() + "," + order.getDirection().name().toLowerCase(Locale.ROOT)));
                    return uri.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(new ParameterizedTypeReference<RemotePage<RemoteClaim>>() {
                }));
    }

    @Override
    public RemoteClaim getClaim(Long claimId) {
        return call(() -> restClient.get()
                .uri(CLAIMS_API + "/{id}", claimId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(RemoteClaim.class));
    }

    @Override
    public RemoteClaim assign(Long claimId) {
        return call(() -> restClient.post()
                .uri(CLAIMS_API + "/{id}/assign", claimId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(RemoteClaim.class));
    }

    @Override
    public RemoteClaim approve(Long claimId, String comment) {
        return postDecision(claimId, "approve", new ApproveBody(comment));
    }

    @Override
    public RemoteClaim reject(Long claimId, String reason) {
        return postDecision(claimId, "reject", new RejectBody(reason));
    }

    @Override
    public RemoteClaim settle(Long claimId, String comment) {
        return postDecision(claimId, "settle", new SettleBody(comment));
    }

    @Override
    public RemoteClaim requestInfo(Long claimId, String message) {
        return postDecision(claimId, "request-info", new RequestInfoBody(message));
    }

    @Override
    public RemoteStatistics getStatistics(StatisticsScope scope) {
        return call(() -> restClient.get()
                .uri(uri -> {
                    uri.path(CLAIMS_API + "/statistics");
                    addFilters(uri, scope, false);
                    return uri.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(RemoteStatistics.class));
    }

    @Override
    public RemoteTrend getTrend(StatisticsScope scope) {
        return call(() -> restClient.get()
                .uri(uri -> {
                    uri.path(CLAIMS_API + "/trend");
                    addFilters(uri, scope, true);
                    return uri.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(RemoteTrend.class));
    }

    /** Only the filters that are set go on the URL, so an unfiltered dashboard asks for exactly what it always did. */
    private static void addFilters(UriBuilder uri, StatisticsScope scope, boolean includeMarket) {
        if (includeMarket && scope.market() != null) {
            uri.queryParam("market", scope.market());
        }
        if (scope.claimType() != null) {
            uri.queryParam("claimType", scope.claimType());
        }
        if (scope.fromDate() != null) {
            uri.queryParam("fromDate", scope.fromDate());
        }
        if (scope.toDate() != null) {
            uri.queryParam("toDate", scope.toDate());
        }
    }

    private RemoteClaim postDecision(Long claimId, String action, Object body) {
        return call(() -> restClient.post()
                .uri(CLAIMS_API + "/{id}/" + action, claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .body(RemoteClaim.class));
    }

    /** Turns transport-level failures (refused, timed out, unreadable body) into this service's exceptions. */
    private <T> T call(Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new ClaimServiceProtocolException("Claim service returned an empty response");
            }
            return result;
        } catch (ResourceAccessException e) {
            log.warn("Claim service is unreachable: {}", e.getMessage());
            throw new ClaimServiceUnavailableException("Claim service is unavailable; please try again shortly");
        } catch (RestClientException e) {
            log.warn("Claim service sent an unusable response: {}", e.getMessage());
            throw new ClaimServiceProtocolException("Claim service sent an unreadable response");
        }
    }

    private RuntimeException translate(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String message = remoteMessage(response, status);
        log.debug("Claim service answered HTTP {}: {}", status.value(), message);

        return switch (status.value()) {
            case 400 -> new InvalidRequestException(message);
            case 403 -> new ClaimAccessDeniedException(message);
            case 404 -> new ClaimNotFoundException(message);
            case 409 -> new ClaimConflictException(message);
            case 401 -> new ClaimServiceProtocolException("Claim service did not accept the caller's credentials");
            default -> status.is5xxServerError()
                    ? new ClaimServiceUnavailableException("Claim service failed with HTTP " + status.value())
                    : new ClaimServiceProtocolException("Claim service answered with unexpected HTTP " + status.value());
        };
    }

    /** Claim-service's error body is {@code {"message": "..."}}; fall back to the status line when it is not. */
    private String remoteMessage(ClientHttpResponse response, HttpStatusCode status) {
        try {
            JsonNode message = objectMapper.readTree(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8))
                    .path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (IOException | RuntimeException ignored) {
            // body missing or not JSON: use the generic text below
        }
        return "Claim service answered HTTP " + status.value();
    }

    private record ApproveBody(String comment) {
    }

    private record SettleBody(String comment) {
    }

    private record RejectBody(String reason) {
    }

    private record RequestInfoBody(String message) {
    }
}
