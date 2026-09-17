package com.theruzil.gateway.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.theruzil.gateway.config.GatewayProperties;

@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String COOKIE_PATH = "/auth";

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieFactory(GatewayProperties properties) {
        this.secure = properties.getAuth().getCookie().isSecure();
        this.sameSite = properties.getAuth().getCookie().getSameSite();
    }

    public ResponseCookie create(String value, Duration maxAge) {
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie clearing() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .sameSite(sameSite);
    }
}
