package com.actilazion.ariesreportingproject.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class RawJwtAuthenticationToken extends AbstractAuthenticationToken {
    private final String token;

    public RawJwtAuthenticationToken(String token) {
        super(List.of());
        this.token = token;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}
