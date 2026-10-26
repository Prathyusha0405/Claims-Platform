package com.chubb.claims.assessment.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Whether claim-service answers its own health endpoint. Every claims staff action and every dashboard figure comes from it,
 * so without it this service cannot do its job: it is part of the readiness group (and of the overall health), but not of
 * liveness. The call carries no token (the endpoint is public), and the answer reveals nothing but UP or DOWN.
 */
@Component
public class ClaimServiceHealthIndicator implements HealthIndicator {

    private final RestClient claimServiceRestClient;

    public ClaimServiceHealthIndicator(@Qualifier("claimServiceRestClient") RestClient claimServiceRestClient) {
        this.claimServiceRestClient = claimServiceRestClient;
    }

    @Override
    public Health health() {
        try {
            claimServiceRestClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (RestClientException unreachableOrUnhealthy) {
            return Health.down().withDetail("error", unreachableOrUnhealthy.getClass().getSimpleName()).build();
        }
    }
}
