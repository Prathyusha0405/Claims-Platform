package com.chubb.claims.auth.security;

import com.chubb.claims.auth.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** The principal placed in the SecurityContext once a bearer token has been validated. */
public record AuthenticatedUser(Long userId, String email, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
