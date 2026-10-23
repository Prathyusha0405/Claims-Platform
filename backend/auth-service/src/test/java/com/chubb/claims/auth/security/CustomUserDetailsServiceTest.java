package com.chubb.claims.auth.security;

import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import com.chubb.claims.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadsAnAccountByEmailAsSpringSecurityUserDetails() {
        User user = new User("Jane Tan", "jane@example.com", "$2a$10$hash", Role.CLAIMS_STAFF);
        when(userService.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("jane@example.com");

        assertThat(details.getUsername()).isEqualTo("jane@example.com");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CLAIMS_STAFF");
        assertThat(details).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) details).user()).isSameAs(user);
    }

    @Test
    void leavesEmailMatchingRulesToTheUserService() {
        when(userService.findByEmail("  Jane@Example.COM ")).thenReturn(Optional.of(new User("Jane", "jane@example.com", "h", Role.CLAIMANT)));

        assertThat(userDetailsService.loadUserByUsername("  Jane@Example.COM ").getUsername()).isEqualTo("jane@example.com");

        verify(userService).findByEmail("  Jane@Example.COM ");
    }

    @Test
    void anUnknownEmailIsUsernameNotFound() {
        when(userService.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleMapsToItsRolePrefixedAuthority(Role role) {
        UserPrincipal principal = new UserPrincipal(new User("X", "x@example.com", "h", role));

        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + role.name());
    }

    @Test
    void accountsAreAlwaysUsableUntilStatusFlagsExist() {
        UserPrincipal principal = new UserPrincipal(new User("X", "x@example.com", "h", Role.CLAIMANT));

        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }
}
