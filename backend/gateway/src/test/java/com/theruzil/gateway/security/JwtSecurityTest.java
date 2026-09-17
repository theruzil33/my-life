package com.theruzil.gateway.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.theruzil.gateway.testsupport.CsrfTestSupport.csrf;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.theruzil.gateway.config.GatewayProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityTest {

    private static final String OTHER_SECRET = "a-completely-different-secret-with-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void registrationEndpointDoesNotRequireToken() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void loginEndpointDoesNotRequireToken() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRequestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/tracker/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRequestWithInvalidSignatureIsUnauthorized() throws Exception {
        String token = tokenSignedWith(OTHER_SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/tracker/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRequestWithExpiredTokenIsUnauthorized() throws Exception {
        String token = tokenSignedWith(gatewayProperties.getJwt().getSecret(),
                Instant.now().minusSeconds(60));

        mockMvc.perform(get("/tracker/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String tokenSignedWith(String secret, Instant expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("1")
                .issuedAt(Date.from(expiration.minusSeconds(3600)))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
