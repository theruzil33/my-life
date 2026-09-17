package com.theruzil.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private LoginService loginService;

    private User user;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordEncoder);
        user = new User("user@example.com", passwordEncoder.encode("password123"), Instant.now());
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void returnsUserForValidCredentials() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        User result = loginService.login("User@Example.com", "password123");

        assertThat(result).isSameAs(user);
    }

    @Test
    void rejectsLoginWithWrongPassword() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login("user@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsLoginForUnknownEmailWithSameError() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login("missing@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(userRepository).findByEmail("missing@example.com");
    }
}
