package com.chubb.claims.auth.service;

import com.chubb.claims.auth.dto.UserResponse;
import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.entity.User;
import com.chubb.claims.auth.exception.EmailAlreadyExistsException;
import com.chubb.claims.auth.exception.UserNotFoundException;
import com.chubb.claims.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserNormalizesEmailHashesPasswordAndSavesWithRequestedRole() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser("  Jane Tan ", "  Jane@Example.COM ", "Secret123", Role.CLAIMS_STAFF);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("Jane Tan");
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CLAIMS_STAFF);
        assertThat(created).isSameAs(saved.getValue());
    }

    @Test
    void createUserRejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("Jane", "JANE@example.com", "Secret123", Role.CLAIMANT))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createUserTranslatesUniqueConstraintRaceIntoEmailAlreadyExists() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> userService.createUser("Jane", "jane@example.com", "Secret123", Role.CLAIMANT))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void findByEmailNormalizesBeforeLookup() {
        User user = new User("Jane", "jane@example.com", "hashed", Role.CLAIMANT);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThat(userService.findByEmail("  JANE@Example.com ")).containsSame(user);
    }

    @Test
    void getCurrentUserReturnsProfileWithoutPasswordHash() {
        User user = new User("Jane", "jane@example.com", "hashed", Role.CLAIMANT);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(Role.CLAIMANT);
        assertThat(response.toString()).doesNotContain("hashed");
    }

    @Test
    void getCurrentUserThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(99L)).isInstanceOf(UserNotFoundException.class);
    }
}
