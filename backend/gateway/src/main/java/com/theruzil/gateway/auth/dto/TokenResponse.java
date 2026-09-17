package com.theruzil.gateway.auth.dto;

import java.time.Duration;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String email) {

    public static TokenResponse of(String accessToken, Duration accessTokenTtl, String email) {
        return new TokenResponse(accessToken, "Bearer", accessTokenTtl.toSeconds(), email);
    }
}
