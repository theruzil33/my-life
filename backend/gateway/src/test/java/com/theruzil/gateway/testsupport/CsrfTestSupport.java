package com.theruzil.gateway.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.stream.Stream;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

public final class CsrfTestSupport {

    public static final String COOKIE_NAME = "XSRF-TOKEN";

    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private CsrfTestSupport() {
    }

    public static RequestPostProcessor csrf(MockMvc mockMvc) {
        return request -> {
            try {
                MockHttpServletResponse csrfResponse = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse();
                Cookie cookie = csrfResponse.getCookie(COOKIE_NAME);
                if (cookie == null) {
                    throw new IllegalStateException("CSRF bootstrap did not set the " + COOKIE_NAME + " cookie");
                }
                Cookie[] existing = request.getCookies();
                Cookie[] merged = existing == null ? new Cookie[] { cookie }
                        : Stream.concat(Stream.of(existing), Stream.of(cookie)).toArray(Cookie[]::new);
                request.setCookies(merged);
                request.addHeader(HEADER_NAME, cookie.getValue());
                return request;
            }
            catch (Exception ex) {
                throw new IllegalStateException("Failed to bootstrap a CSRF token", ex);
            }
        };
    }
}
