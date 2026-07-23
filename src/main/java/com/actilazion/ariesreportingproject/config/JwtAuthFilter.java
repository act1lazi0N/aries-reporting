package com.actilazion.ariesreportingproject.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final AuthenticationManager authenticationManager;
    private final BearerTokenResolver bearerTokenResolver;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        BearerTokenResolver.Resolution bearerToken = bearerTokenResolver.resolve(request);
        if (!bearerToken.credentialsFound()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (bearerToken.invalid()) {
            reject(response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                RawJwtAuthenticationToken rawToken = new RawJwtAuthenticationToken(bearerToken.token());
                rawToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                var authentication = authenticationManager.authenticate(rawToken);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationException e) {
                reject(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.flushBuffer();
    }
}
