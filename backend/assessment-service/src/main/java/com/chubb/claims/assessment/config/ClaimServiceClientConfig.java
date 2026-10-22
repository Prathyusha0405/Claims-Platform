package com.chubb.claims.assessment.config;

import com.chubb.claims.assessment.client.BearerTokenForwardingInterceptor;
import com.chubb.claims.assessment.client.ClaimServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClaimServiceClientConfig {

    @Bean
    public RestClient claimServiceRestClient(RestClient.Builder builder, ClaimServiceProperties properties) {
        return restClient(builder, properties);
    }

    /** Public so the client can also be assembled by hand in tests. */
    public static RestClient restClient(RestClient.Builder builder, ClaimServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenForwardingInterceptor())
                .build();
    }
}
