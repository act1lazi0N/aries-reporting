package com.actilazion.ariesreportingproject.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    @Mock
    JwtService jwtService;
    @Mock
    UserDetailsService userDetailsService;
    @Mock
    FilterChain filterChain;
    private final BearerTokenResolver bearerTokenResolver = new BearerTokenResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter: rejects invalid bearer token with 401")
    void doFilter_invalidBearerToken_rejects() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService, bearerTokenResolver);
        MockHttpServletRequest request = bearerRequest("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("bad-token"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter: rejects locked user with 401")
    void doFilter_lockedUser_rejects() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService, bearerTokenResolver);
        MockHttpServletRequest request = bearerRequest("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var lockedUser = User.withUsername("locked@aries.local")
                .password("n/a")
                .roles("USER")
                .accountLocked(true)
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("locked@aries.local");
        when(userDetailsService.loadUserByUsername("locked@aries.local")).thenReturn(lockedUser);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid("valid-token", lockedUser);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter: authenticates usable user and continues")
    void doFilter_validToken_authenticatesAndContinues() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService, bearerTokenResolver);
        MockHttpServletRequest request = bearerRequest("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@aries.local");
        when(userDetailsService.loadUserByUsername("user@aries.local")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter: rejects empty bearer token before JWT parsing")
    void doFilter_emptyBearerToken_rejectsBeforeParsing() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService, bearerTokenResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verifyNoInteractions(jwtService, userDetailsService);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter: accepts bearer scheme case-insensitively")
    void doFilter_lowercaseBearer_authenticates() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService, bearerTokenResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@aries.local");
        when(userDetailsService.loadUserByUsername("user@aries.local")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
