package com.theruzil.gateway.security;

import static com.theruzil.gateway.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class CsrfProtectionTest {

    private static final String COOKIE_NAME = "XSRF-TOKEN";

    private static final String HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfEndpointBootstrapIsPublicAndServesToken() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(Matchers.not(Matchers.blankOrNullString())))
                .andExpect(jsonPath("$.headerName").value(HEADER_NAME));
    }

    @Test
    void mutatingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequestWithMismatchedCsrfTokenIsRejected() throws Exception {
        Cookie cookie = csrfCookie();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .cookie(cookie)
                .header(HEADER_NAME, "not-the-issued-token")
                .content(registerBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequestWithValidCsrfTokenIsAccepted() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content(registerBody()))
                .andExpect(status().isCreated());
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/csrf")).andReturn();
        MockHttpServletResponse response = result.getResponse();
        return response.getCookie(COOKIE_NAME);
    }

    private String registerBody() {
        return "{\"email\":\"" + "user-" + UUID.randomUUID() + "@example.com" + "\",\"password\":\"password123\"}";
    }
}
