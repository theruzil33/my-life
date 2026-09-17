package com.theruzil.gateway.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(candidate -> password != null && passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return jwtService.issueAccessToken(user.getId());
    }
}
