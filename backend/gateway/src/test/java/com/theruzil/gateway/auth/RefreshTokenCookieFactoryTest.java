package com.theruzil.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.theruzil.gateway.config.GatewayProperties;

class RefreshTokenCookieFactoryTest {

    @Test
    void createsHttpOnlyCookieScopedToAuthWithConfiguredSecureAndSameSite() {
        RefreshTokenCookieFactory factory = factory(true, "Strict");

        ResponseCookie cookie = factory.create("token-value", Duration.ofDays(7));

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void honorsRelaxedCookieSettingsForDevelopment() {
        RefreshTokenCookieFactory factory = factory(false, "Lax");

        ResponseCookie cookie = factory.create("token-value", Duration.ofHours(1));

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void clearingCookieExpiresImmediatelyAndKeepsScoping() {
        RefreshTokenCookieFactory factory = factory(true, "Strict");

        ResponseCookie cookie = factory.clearing();

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
    }

    private RefreshTokenCookieFactory factory(boolean secure, String sameSite) {
        GatewayProperties properties = new GatewayProperties();
        properties.getAuth().getCookie().setSecure(secure);
        properties.getAuth().getCookie().setSameSite(sameSite);
        return new RefreshTokenCookieFactory(properties);
    }
}
