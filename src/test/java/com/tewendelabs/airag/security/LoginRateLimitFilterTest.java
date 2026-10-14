
package com.tewendelabs.airag.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void shouldAllowFirstFiveLoginAttempts()
            throws ServletException, IOException {

        for (int i = 0; i < 5; i++) {
            var request = loginRequest("192.0.2.10");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void shouldReturn429AfterFiveAttempts()
            throws ServletException, IOException {

        for (int i = 0; i < 5; i++) {
            filter.doFilter(
                    loginRequest("192.0.2.10"),
                    new MockHttpServletResponse(),
                    chain);
        }

        var response = new MockHttpServletResponse();

        filter.doFilter(
                loginRequest("192.0.2.10"),
                response,
                chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString()
                .contains("TOO_MANY_REQUESTS"));

        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void shouldMaintainIndependentBucketsPerIp()
            throws ServletException, IOException {

        for (int i = 0; i < 5; i++) {
            filter.doFilter(
                    loginRequest("192.0.2.10"),
                    new MockHttpServletResponse(),
                    chain);
        }

        var response = new MockHttpServletResponse();

        filter.doFilter(
                loginRequest("192.0.2.11"),
                response,
                chain);

        assertEquals(200, response.getStatus());
        verify(chain, times(6)).doFilter(any(), any());
    }

    @Test
    void shouldNotLimitOtherEndpoints()
            throws ServletException, IOException {

        var request = new MockHttpServletRequest(
                "GET", "/api/auth/me");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
    }


    private MockHttpServletRequest loginRequest(String ip) {
        var request = new MockHttpServletRequest(
                "POST", "/api/auth/login");

        request.setServletPath("/api/auth/login");
        request.setRemoteAddr(ip);

        return request;
    }

}
