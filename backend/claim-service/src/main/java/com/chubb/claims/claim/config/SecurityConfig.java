package com.chubb.claims.claim.config;

import com.chubb.claims.claim.security.JsonSecurityErrorHandler;
import com.chubb.claims.claim.security.JwtAuthenticationFilter;
import com.chubb.claims.claim.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Stateless, bearer-token API security. Tokens are issued by auth-service; this service only validates them.
 * <ul>
 *   <li>Creating claims, listing "my claims" and answering an information request are CLAIMANT actions.</li>
 *   <li>Viewing and searching claims is open to every role; the service layer narrows claimants to their own claims.</li>
 *   <li>Notifications are open to every role; the service layer only ever shows and changes the caller's own.</li>
 *   <li>{@code /api/internal/**} is for assessment-service acting on behalf of claims staff: claims staff act on
 *       claims, managers read.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CLAIMANT = "CLAIMANT";
    private static final String CLAIMS_STAFF = "CLAIMS_STAFF";
    private static final String MANAGER = "MANAGER";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper)
            throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(objectMapper);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                paths.matcher("/swagger-ui/**"),
                                paths.matcher("/swagger-ui.html"),
                                paths.matcher("/v3/api-docs/**"),
                                paths.matcher("/h2-console/**"),
                                paths.matcher("/error")).permitAll()
                        .requestMatchers(
                                paths.matcher(HttpMethod.POST, "/api/claims"),
                                paths.matcher(HttpMethod.GET, "/api/claims/my-claims"),
                                paths.matcher(HttpMethod.POST, "/api/claims/*/additional-info")).hasRole(CLAIMANT)
                        .requestMatchers(
                                paths.matcher(HttpMethod.GET, "/api/claims/search"),
                                paths.matcher(HttpMethod.GET, "/api/claims/*")).hasAnyRole(CLAIMANT, CLAIMS_STAFF, MANAGER)
                        .requestMatchers(
                                paths.matcher(HttpMethod.GET, "/api/notifications"),
                                paths.matcher(HttpMethod.GET, "/api/notifications/unread-count"),
                                paths.matcher(HttpMethod.POST, "/api/notifications/read-all"),
                                paths.matcher(HttpMethod.POST, "/api/notifications/*/read")).hasAnyRole(CLAIMANT, CLAIMS_STAFF, MANAGER)
                        .requestMatchers(
                                paths.matcher(HttpMethod.GET, "/api/internal/claims/statistics"),
                                paths.matcher(HttpMethod.GET, "/api/internal/claims/*")).hasAnyRole(CLAIMS_STAFF, MANAGER)
                        .requestMatchers(
                                paths.matcher(HttpMethod.POST, "/api/internal/claims/*/*")).hasRole(CLAIMS_STAFF)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
