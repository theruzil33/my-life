package com.theruzil.gateway.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User login(String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .filter(candidate -> password != null && passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
    }
}
