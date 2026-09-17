package com.theruzil.gateway.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        userRepository.saveAndFlush(new User("user@example.com", "hash", Instant.now()));

        Optional<User> found = userRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findsUserIgnoringEmailCase() {
        userRepository.saveAndFlush(new User("Mixed.Case@Example.COM", "hash", Instant.now()));

        assertThat(userRepository.findByEmail("mixed.case@example.com")).isPresent();
        assertThat(userRepository.findByEmail("MIXED.CASE@EXAMPLE.COM")).isPresent();
    }

    @Test
    void storesNormalizedEmail() {
        User saved = userRepository.saveAndFlush(new User("  Upper@Example.Com  ", "hash", Instant.now()));

        assertThat(saved.getEmail()).isEqualTo("upper@example.com");
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() {
        userRepository.saveAndFlush(new User("duplicate@example.com", "hash", Instant.now()));

        assertThatThrownBy(
                () -> userRepository.saveAndFlush(new User("DUPLICATE@example.com", "hash", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsEmptyForUnknownEmail() {
        assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
    }
}
