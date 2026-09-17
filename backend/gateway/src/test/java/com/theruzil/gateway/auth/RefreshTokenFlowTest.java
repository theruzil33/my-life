package com.theruzil.gateway.auth;

import static com.theruzil.gateway.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenFlowTest {

    private static final String COOKIE_NAME = RefreshTokenCookieFactory.COOKIE_NAME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void refreshRotatesTokenAndRejectsReuseOfTheOldToken() throws Exception {
        Cookie firstCookie = login(registerNewUser());

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();
        Cookie secondCookie = refreshResult.getResponse().getCookie(COOKIE_NAME);

        org.assertj.core.api.Assertions.assertThat(secondCookie.getValue())
                .isNotEqualTo(firstCookie.getValue());

        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(firstCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(secondCookie))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(new Cookie(COOKIE_NAME, "unknown-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithExpiredTokenIsRejected() throws Exception {
        String rawToken = "expired-" + UUID.randomUUID();
        Instant now = Instant.now();
        refreshTokenRepository.save(new RefreshToken(1L, RefreshTokenService.hash(rawToken), now.minus(Duration.ofDays(8)),
                now.minus(Duration.ofDays(1))));

        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(new Cookie(COOKIE_NAME, rawToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTokenClearsCookieAndPreventsRefresh() throws Exception {
        Cookie cookie = login(registerNewUser());

        mockMvc.perform(post("/auth/logout").with(csrf(mockMvc)).cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));

        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieSucceedsWithoutServerError() throws Exception {
        mockMvc.perform(post("/auth/logout").with(csrf(mockMvc)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutAffectsOnlyThePresentedSession() throws Exception {
        String email = registerNewUser();
        Cookie firstSession = login(email);
        Cookie secondSession = login(email);

        mockMvc.perform(post("/auth/logout").with(csrf(mockMvc)).cookie(firstSession))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").with(csrf(mockMvc)).cookie(secondSession))
                .andExpect(status().isOk());
    }

    private String registerNewUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(COOKIE_NAME);
    }
}
