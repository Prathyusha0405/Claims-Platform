package com.chubb.claims.assessment.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where claim-service lives and how long to wait for it.
 *
 * @param baseUrl        e.g. {@code http://localhost:8082}
 * @param connectTimeout how long to wait to open a connection (default 2s)
 * @param readTimeout    how long to wait for a response (default 5s)
 */
@ConfigurationProperties(prefix = "app.claim-service")
public record ClaimServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ClaimServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.claim-service.base-url must be set");
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
