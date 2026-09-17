package com.theruzil.gateway.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

        mockMvc.perform(post("/auth/register")
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

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRegistrationWithDifferentEmailCase() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email.toUpperCase(), "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRegistrationWithInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("not-an-email", "password123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRegistrationWithTooShortPassword() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueEmail(), "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsInWithValidCredentials() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void rejectsLoginForUnknownEmailWithSameMessage() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(uniqueEmail(), "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
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
