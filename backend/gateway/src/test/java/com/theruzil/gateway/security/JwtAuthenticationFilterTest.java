package com.theruzil.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.theruzil.gateway.auth.JwtService;
import com.theruzil.gateway.config.GatewayProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService(properties(), Clock.systemUTC());

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesAuthenticatedUserIdInRequestContext() throws Exception {
        String token = jwtService.issueAccessToken(42L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tracker/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> requestAttribute = new AtomicReference<>();
        AtomicReference<Object> principal = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            requestAttribute.set(
                    ((HttpServletRequest) servletRequest).getAttribute(JwtAuthenticationFilter.USER_ID_REQUEST_ATTRIBUTE));
            principal.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        };

        filter.doFilter(request, response, chain);

        assertThat(requestAttribute.get()).isEqualTo(42L);
        assertThat(principal.get()).isEqualTo(new AuthenticatedUser(42L));
    }

    @Test
    void rejectsInvalidTokenWithoutContinuingFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tracker/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);
        FilterChain chain = (servletRequest, servletResponse) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNotFilterPublicAuthenticationEndpoints() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/register"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/tracker/items"))).isFalse();
    }

    private GatewayProperties properties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getJwt().setSecret("test-gateway-jwt-secret-with-at-least-32-bytes");
        properties.getJwt().setAccessTokenTtl(Duration.ofHours(1));
        return properties;
    }
}
