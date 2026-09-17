package com.theruzil.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final List<Route> routes = new ArrayList<>();
    private final Jwt jwt = new Jwt();
    private final Auth auth = new Auth();

    public List<Route> getRoutes() {
        return routes;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Auth getAuth() {
        return auth;
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
        private Duration accessTokenTtl = Duration.ofMinutes(15);

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

    public static class Auth {

        private Duration refreshTokenTtl = Duration.ofDays(7);
        private final Cookie cookie = new Cookie();

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public Cookie getCookie() {
            return cookie;
        }

        public static class Cookie {

            private boolean secure = true;
            private String sameSite = "Strict";

            public boolean isSecure() {
                return secure;
            }

            public void setSecure(boolean secure) {
                this.secure = secure;
            }

            public String getSameSite() {
                return sameSite;
            }

            public void setSameSite(String sameSite) {
                this.sameSite = sameSite;
            }
        }
    }
}
