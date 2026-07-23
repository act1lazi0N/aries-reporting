package com.actilazion.ariesreportingproject.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {
    @Mock
    JwtService jwtService;
    @Mock
    UserDetailsService userDetailsService;
    private final JwtAuthorityConverter authorityConverter = new JwtAuthorityConverter();

    @Test
    @DisplayName("supports: accepts raw JWT tokens")
    void supports_rawJwtToken_returnsTrue() {
        JwtAuthenticationProvider provider = provider();

        assertThat(provider.supports(RawJwtAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("authenticate: valid token returns authenticated user details")
    void authenticate_validToken_returnsAuthentication() {
        JwtAuthenticationProvider provider = provider();
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@aries.local");
        when(userDetailsService.loadUserByUsername("user@aries.local")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);

        var authentication = provider.authenticate(new RawJwtAuthenticationToken("valid-token"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("authenticate: locked user is rejected")
    void authenticate_lockedUser_rejects() {
        JwtAuthenticationProvider provider = provider();
        var lockedUser = User.withUsername("locked@aries.local")
                .password("n/a")
                .roles("USER")
                .accountLocked(true)
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("locked@aries.local");
        when(userDetailsService.loadUserByUsername("locked@aries.local")).thenReturn(lockedUser);

        assertThatThrownBy(() -> provider.authenticate(new RawJwtAuthenticationToken("valid-token")))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("authenticate: token subject mismatch is rejected")
    void authenticate_subjectMismatch_rejects() {
        JwtAuthenticationProvider provider = provider();
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@aries.local");
        when(userDetailsService.loadUserByUsername("user@aries.local")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(new RawJwtAuthenticationToken("valid-token")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("JWT subject does not match user");
    }

    private JwtAuthenticationProvider provider() {
        return new JwtAuthenticationProvider(jwtService, userDetailsService, authorityConverter);
    }
}
