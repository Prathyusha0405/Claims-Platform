package com.chubb.claims.assessment.config;

import com.chubb.claims.assessment.security.JsonSecurityErrorHandler;
import com.chubb.claims.assessment.security.JwtAuthenticationFilter;
import com.chubb.claims.assessment.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 *   <li>{@code /api/officer/**}: OFFICER only (work queue, assignment, decisions, notes).</li>
 *   <li>{@code /api/dashboard/**}: ADMIN only.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String OFFICER = "OFFICER";
    private static final String ADMIN = "ADMIN";

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
                        .requestMatchers(paths.matcher("/api/officer/**")).hasRole(OFFICER)
                        .requestMatchers(paths.matcher("/api/dashboard/**")).hasRole(ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
