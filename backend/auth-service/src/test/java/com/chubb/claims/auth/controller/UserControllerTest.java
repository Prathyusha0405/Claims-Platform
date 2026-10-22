package com.chubb.claims.auth.controller;

import com.chubb.claims.auth.config.SecurityConfig;
import com.chubb.claims.auth.dto.UserResponse;
import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.exception.UserNotFoundException;
import com.chubb.claims.auth.security.AuthenticatedUser;
import com.chubb.claims.auth.security.JwtService;
import com.chubb.claims.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    /** Present in the real app; stands in here so Boot does not create its default user and log a password. */
    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void meReturnsTheProfileOfTheAuthenticatedUser() throws Exception {
        AuthenticatedUser principal = new AuthenticatedUser(7L, "officer@chubb.com", Role.OFFICER);
        when(userService.getCurrentUser(7L))
                .thenReturn(new UserResponse(7L, "Demo Officer", "officer@chubb.com", Role.OFFICER, Instant.parse("2026-10-22T00:00:00Z")));

        mockMvc.perform(get("/api/users/me").with(authentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("officer@chubb.com"))
                .andExpect(jsonPath("$.role").value("OFFICER"));
    }

    @Test
    void meWithoutAuthenticationReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"));

        verifyNoInteractions(userService);
    }

    @Test
    void meForADeletedUserReturns401SoTheClientReauthenticates() throws Exception {
        AuthenticatedUser principal = new AuthenticatedUser(99L, "gone@example.com", Role.CLAIMANT);
        when(userService.getCurrentUser(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/me").with(authentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))))
                .andExpect(status().isUnauthorized());
    }
}
