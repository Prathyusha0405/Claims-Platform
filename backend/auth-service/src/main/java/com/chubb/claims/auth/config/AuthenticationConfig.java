package com.chubb.claims.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

/**
 * Exposes the {@link AuthenticationManager} Spring Security builds from the {@code CustomUserDetailsService} and
 * {@code PasswordEncoder} beans (a DaoAuthenticationProvider). It also hides whether an email exists and does the
 * dummy password hash for unknown emails, so wrong-email and wrong-password logins look and time the same.
 * <p>
 * Kept apart from {@link SecurityConfig} so web-layer tests that import only the filter chain need no user store.
 */
@Configuration
public class AuthenticationConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
