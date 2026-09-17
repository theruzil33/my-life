package com.theruzil.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.theruzil.gateway.config.GatewayProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "test-gateway-jwt-secret-with-at-least-32-bytes";
    private static final String OTHER_SECRET = "another-test-secret-that-is-long-enough-32+";
    private static final Duration TTL = Duration.ofHours(1);

    private final JwtService jwtService = new JwtService(properties(SECRET, TTL), Clock.systemUTC());

    @Test
    void extractsUserIdFromValidToken() {
        String token = jwtService.issueAccessToken(42L);

        assertThat(jwtService.verifyAndExtractUserId(token)).isEqualTo(42L);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService otherIssuer = new JwtService(properties(OTHER_SECRET, TTL), Clock.systemUTC());
        String token = otherIssuer.issueAccessToken(42L);

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService expiredIssuer = new JwtService(properties(SECRET, TTL), twoHoursAgo);
        String token = expiredIssuer.issueAccessToken(42L);

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTokenWithNonNumericSubject() {
        String token = Jwts.builder()
                .subject("not-a-number")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(TTL)))
                .signWith(signingKey(SECRET))
                .compact();

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    private GatewayProperties properties(String secret, Duration ttl) {
        GatewayProperties properties = new GatewayProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setAccessTokenTtl(ttl);
        return properties;
    }

    private SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
