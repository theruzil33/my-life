package com.theruzil.gateway.auth;

import static com.theruzil.gateway.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersNewUserWithoutExposingPassword() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsRegistrationWithDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        String body = registerBody(email, "password123");

        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRegistrationWithDifferentEmailCase() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email.toUpperCase(), "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRegistrationWithInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("not-an-email", "password123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRegistrationWithTooShortPassword() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueEmail(), "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsInWithValidCredentialsReturnsAccessTokenAndEmailAndSetsHttpOnlyRefreshCookie() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(cookie().exists(RefreshTokenCookieFactory.COOKIE_NAME))
                .andExpect(cookie().httpOnly(RefreshTokenCookieFactory.COOKIE_NAME, true))
                .andExpect(cookie().path(RefreshTokenCookieFactory.COOKIE_NAME, "/auth"));
    }

    @Test
    void rejectsLoginWithWrongPasswordWithoutSettingRefreshCookie() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(cookie().doesNotExist(RefreshTokenCookieFactory.COOKIE_NAME));
    }

    @Test
    void rejectsLoginForUnknownEmailWithSameMessage() throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(uniqueEmail(), "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void csrfEndpointReturnsTokenAndSetsReadableCookieWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(Matchers.not(Matchers.blankOrNullString())))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String loginBody(String email, String password) {
        return registerBody(email, password);
    }
}
