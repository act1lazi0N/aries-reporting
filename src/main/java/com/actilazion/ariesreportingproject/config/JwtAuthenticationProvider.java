package com.actilazion.ariesreportingproject.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtAuthorityConverter authorityConverter;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String token = (String) authentication.getCredentials();
        try {
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!isAccountUsable(userDetails)) {
                throw new LockedException("User account is not usable");
            }
            if (!jwtService.isTokenValid(token, userDetails)) {
                throw new BadCredentialsException("JWT subject does not match user");
            }

            var authenticated = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    authorityConverter.convert(userDetails));
            authenticated.setDetails(authentication.getDetails());
            return authenticated;
        } catch (AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid JWT", ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RawJwtAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean isAccountUsable(UserDetails userDetails) {
        return userDetails.isEnabled()
                && userDetails.isAccountNonExpired()
                && userDetails.isAccountNonLocked()
                && userDetails.isCredentialsNonExpired();
    }
}
