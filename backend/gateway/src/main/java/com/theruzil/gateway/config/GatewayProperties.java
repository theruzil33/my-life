package com.theruzil.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final List<Route> routes = new ArrayList<>();
    private final Jwt jwt = new Jwt();

    public List<Route> getRoutes() {
        return routes;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static class Route {

        private String prefix;
        private String targetBaseUrl;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getTargetBaseUrl() {
            return targetBaseUrl;
        }

        public void setTargetBaseUrl(String targetBaseUrl) {
            this.targetBaseUrl = targetBaseUrl;
        }
    }

    public static class Jwt {

        private String secret;
        private Duration accessTokenTtl = Duration.ofHours(1);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }
    }
}
