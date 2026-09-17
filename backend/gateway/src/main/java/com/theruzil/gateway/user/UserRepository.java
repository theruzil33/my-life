package com.theruzil.gateway.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    default Optional<User> findByEmail(String email) {
        return findByNormalizedEmail(User.normalizeEmail(email));
    }

    @Query("select u from User u where u.email = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);
}
