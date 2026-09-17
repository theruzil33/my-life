package com.theruzil.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void registersNewUserWithHashedPasswordAndNormalizedEmail() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = registrationService.register("User@Example.com", "password123");

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsRegistrationWithExistingEmail() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User("user@example.com", "hash", Instant.now())));

        assertThatThrownBy(() -> registrationService.register("user@example.com", "password123"))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsRegistrationWithInvalidEmail() {
        assertThatThrownBy(() -> registrationService.register("not-an-email", "password123"))
                .isInstanceOf(InvalidRegistrationDataException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsRegistrationWithTooShortPassword() {
        assertThatThrownBy(() -> registrationService.register("user@example.com", "short"))
                .isInstanceOf(InvalidRegistrationDataException.class);

        verifyNoInteractions(userRepository);
    }
}
