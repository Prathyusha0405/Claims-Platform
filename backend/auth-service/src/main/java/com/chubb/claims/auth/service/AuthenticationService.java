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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthenticationService(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Public self-registration always creates a CLAIMANT; officers and admins are never self-assigned. */
    public UserResponse register(RegisterRequest request) {
        User user = userService.createUser(request.fullName(), request.email(), request.password(), Role.CLAIMANT);
        return UserResponse.from(user);
    }

    /**
     * Verifies the credentials through Spring Security and issues a JWT. Unknown email and wrong password both
     * surface as {@link BadCredentialsException}, which this method reports identically as 401 so a caller cannot
     * tell which one it was. Anything else (for example the database being down) is deliberately not caught here:
     * that is a server fault, not a failed login.
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Authentication produced an unexpected principal type");
        }
        User user = principal.user();
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expiresInSeconds(), UserResponse.from(user));
    }
}
