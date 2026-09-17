package com.theruzil.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.theruzil.gateway.config.GatewayProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(GatewayProperties properties, Clock clock) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "gateway.jwt.secret must be configured with at least " + MINIMUM_SECRET_LENGTH_BYTES + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.getJwt().getAccessTokenTtl();
        this.clock = clock;
    }

    public String issueAccessToken(Long userId) {
        Instant issuedAt = Instant.now(clock);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public Long verifyAndExtractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        }
        catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException(ex);
        }
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
}
