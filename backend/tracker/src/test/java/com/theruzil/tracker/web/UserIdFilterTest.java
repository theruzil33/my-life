package com.theruzil.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserIdFilterTest {

    private final UserIdFilter filter = new UserIdFilter();

    @Test
    void rejectsMissingUserIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/habits");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("authentication required");
        assertThat(request.getAttribute(UserIdFilter.USER_ID_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsNonNumericUserIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/habits");
        request.addHeader(UserIdFilter.USER_ID_HEADER, "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(UserIdFilter.USER_ID_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void exposesUserIdAttributeForValidHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/habits");
        request.addHeader(UserIdFilter.USER_ID_HEADER, " 42 ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(UserIdFilter.USER_ID_ATTRIBUTE)).isEqualTo(42L);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
