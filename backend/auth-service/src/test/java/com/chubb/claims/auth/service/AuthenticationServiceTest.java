package com.chubb.claims.auth.service;

import com.chubb.claims.auth.dto.AuthResponse;
import com.chubb.claims.auth.dto.LoginRequest;
import com.chubb.claims.auth.dto.RegisterRequest;
import com.chubb.claims.auth.dto.UserResponse;
import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import com.chubb.claims.auth.exception.InvalidCredentialsException;
import com.chubb.claims.auth.security.JwtService;
import com.chubb.claims.auth.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(userService, authenticationManager, jwtService);
    }

    @Test
    void registerAlwaysCreatesAClaimant() {
        User created = userWithId(1L, "jane@example.com", Role.CLAIMANT);
        when(userService.createUser("Jane Tan", "jane@example.com", "Secret123", Role.CLAIMANT)).thenReturn(created);

        UserResponse response = authenticationService.register(new RegisterRequest("Jane Tan", "jane@example.com", "Secret123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(Role.CLAIMANT);
    }

    @Test
    void loginAuthenticatesThroughSpringSecurityAndIssuesABearerToken() {
        User user = userWithId(1L, "jane@example.com", Role.CLAIMANT);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedAs(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authenticationService.login(new LoginRequest("  Jane@Example.com ", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("jane@example.com");
        assertThat(response.user().role()).isEqualTo(Role.CLAIMANT);
    }

    @Test
    void loginHandsTheCredentialsToTheManagerUntouched() {
        User user = userWithId(1L, "jane@example.com", Role.CLAIMANT);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedAs(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        authenticationService.login(new LoginRequest("  Jane@Example.com ", "Secret123"));

        ArgumentCaptor<Authentication> request = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(request.capture());
        assertThat(request.getValue().isAuthenticated()).isFalse();
        // Email normalization is the user store's rule (UserService), applied in one place for login and registration.
        assertThat(request.getValue().getName()).isEqualTo("  Jane@Example.com ");
        assertThat(request.getValue().getCredentials()).isEqualTo("Secret123");
    }

    @Test
    void badCredentialsBecomeTheSameInvalidCredentialsFailureWhateverTheCause() {
        when(authenticationManager.authenticate(any(Authentication.class))).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("ghost@example.com", "Secret123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void aBrokenUserStoreIsAServerFaultNotAFailedLogin() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new InternalAuthenticationServiceException("database is down"));

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("jane@example.com", "Secret123")))
                .isInstanceOf(InternalAuthenticationServiceException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void anUnexpectedPrincipalTypeIsRefusedRatherThanGuessedAt() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("just-a-string", null, java.util.List.of()));

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("jane@example.com", "Secret123")))
                .isInstanceOf(IllegalStateException.class);

        verify(jwtService, never()).generateToken(any());
    }

    private static Authentication authenticatedAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static User userWithId(Long id, String email, Role role) {
        User user = new User("Jane Tan", email, "hashed", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
