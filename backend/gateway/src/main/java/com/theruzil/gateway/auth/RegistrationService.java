package com.theruzil.gateway.auth;

import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@Service
public class RegistrationService {

    static final int MINIMUM_PASSWORD_LENGTH = 8;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        validate(normalizedEmail, password);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User user = new User(normalizedEmail, passwordEncoder.encode(password), Instant.now());
            return userRepository.save(user);
        }
        catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyUsedException();
        }
    }

    private void validate(String email, String password) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidRegistrationDataException("Email format is invalid");
        }
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new InvalidRegistrationDataException(
                    "Password must contain at least " + MINIMUM_PASSWORD_LENGTH + " characters");
        }
    }
}
