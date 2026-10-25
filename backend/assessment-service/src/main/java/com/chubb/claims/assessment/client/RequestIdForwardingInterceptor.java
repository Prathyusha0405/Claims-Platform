package com.chubb.claims.assessment.client;

import com.chubb.claims.assessment.logging.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Passes the id of the request being served on to claim-service, so a staff action has the same id in both services' logs.
 * Outside a request there is no id and nothing is added.
 */
public class RequestIdForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        if (id != null) {
            request.getHeaders().set(RequestIdFilter.HEADER, id);
        }
        return execution.execute(request, body);
    }
}
