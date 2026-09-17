package com.theruzil.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.gateway.config.GatewayProperties;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, GatewayProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = properties.getAuth().getRefreshTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);
        String value = generateTokenValue();
        refreshTokenRepository.save(new RefreshToken(userId, hash(value), issuedAt, expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken current = findActive(rawToken);
        current.revoke(Instant.now(clock));
        refreshTokenRepository.save(current);

        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);
        String value = generateTokenValue();
        refreshTokenRepository.save(new RefreshToken(current.getUserId(), hash(value), issuedAt, expiresAt));
        return new Rotation(current.getUserId(), value, expiresAt);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(Instant.now(clock));
                refreshTokenRepository.save(token);
            }
        });
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    private RefreshToken findActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is missing");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
        if (!token.isActive(Instant.now(clock))) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }
        return token;
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record Rotation(Long userId, String value, Instant expiresAt) {
    }
}
