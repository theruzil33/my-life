package com.theruzil.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GatewayPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsRoutesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "gateway.routes[0].prefix=/tracker",
                        "gateway.routes[0].target-base-url=http://localhost:8081",
                        "gateway.routes[1].prefix=/notes",
                        "gateway.routes[1].target-base-url=http://localhost:8082")
                .run(context -> {
                    GatewayProperties properties = context.getBean(GatewayProperties.class);

                    assertThat(properties.getRoutes()).hasSize(2);
                    assertThat(properties.getRoutes().get(0).getPrefix()).isEqualTo("/tracker");
                    assertThat(properties.getRoutes().get(0).getTargetBaseUrl())
                            .isEqualTo("http://localhost:8081");
                    assertThat(properties.getRoutes().get(1).getPrefix()).isEqualTo("/notes");
                    assertThat(properties.getRoutes().get(1).getTargetBaseUrl())
                            .isEqualTo("http://localhost:8082");
                });
    }

    @Test
    void bindsJwtSettings() {
        contextRunner
                .withPropertyValues(
                        "gateway.jwt.secret=another-secret-that-is-at-least-32-bytes-long",
                        "gateway.jwt.access-token-ttl=PT30M")
                .run(context -> {
                    GatewayProperties properties = context.getBean(GatewayProperties.class);

                    assertThat(properties.getJwt().getSecret())
                            .isEqualTo("another-secret-that-is-at-least-32-bytes-long");
                    assertThat(properties.getJwt().getAccessTokenTtl()).hasSeconds(1800);
                });
    }

    @Test
    void providesDefaultAccessTokenAndRefreshTokenSettings() {
        contextRunner.run(context -> {
            GatewayProperties properties = context.getBean(GatewayProperties.class);

            assertThat(properties.getJwt().getAccessTokenTtl()).hasSeconds(900);
            assertThat(properties.getAuth().getRefreshTokenTtl()).hasDays(7);
            assertThat(properties.getAuth().getCookie().isSecure()).isTrue();
            assertThat(properties.getAuth().getCookie().getSameSite()).isEqualTo("Strict");
        });
    }

    @Test
    void bindsAuthSettings() {
        contextRunner
                .withPropertyValues(
                        "gateway.auth.refresh-token-ttl=P1D",
                        "gateway.auth.cookie.secure=false",
                        "gateway.auth.cookie.same-site=Lax")
                .run(context -> {
                    GatewayProperties properties = context.getBean(GatewayProperties.class);

                    assertThat(properties.getAuth().getRefreshTokenTtl()).hasDays(1);
                    assertThat(properties.getAuth().getCookie().isSecure()).isFalse();
                    assertThat(properties.getAuth().getCookie().getSameSite()).isEqualTo("Lax");
                });
    }

    @EnableConfigurationProperties(GatewayProperties.class)
    static class PropertiesConfiguration {
    }
}
