package com.theruzil.gateway.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
