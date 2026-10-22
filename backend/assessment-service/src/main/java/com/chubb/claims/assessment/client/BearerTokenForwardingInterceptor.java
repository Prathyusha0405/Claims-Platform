package com.chubb.claims.assessment.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

/**
 * Sends the current caller's own bearer token on every request to claim-service, so claim-service authorizes the
 * officer or admin directly and no service credentials exist. Outside a request (no authentication) nothing is added
 * and claim-service answers 401.
 */
public class BearerTokenForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token && !token.isBlank()) {
            request.getHeaders().setBearerAuth(token);
        }
        return execution.execute(request, body);
    }
}
