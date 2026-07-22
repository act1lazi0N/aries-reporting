package com.actilazion.ariesreportingproject.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class BearerTokenResolver {
    private static final String BEARER_SCHEME = "Bearer";
    private static final int MAX_AUTHORIZATION_HEADER_LENGTH = 8192;
    private static final int MAX_TOKEN_LENGTH = 4096;

    public Resolution resolve(HttpServletRequest request) {
        List<String> headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.isEmpty()) {
            return Resolution.missing();
        }
        if (headers.size() > 1) {
            return Resolution.invalidCredentials();
        }

        String header = headers.getFirst();
        if (header == null || header.isBlank()) {
            return Resolution.missing();
        }
        if (header.length() > MAX_AUTHORIZATION_HEADER_LENGTH || header.contains(",")) {
            return Resolution.invalidCredentials();
        }

        String trimmed = header.trim();
        if (trimmed.equalsIgnoreCase(BEARER_SCHEME)) {
            return Resolution.invalidCredentials();
        }

        int separator = trimmed.indexOf(' ');
        if (separator < 0) {
            return Resolution.missing();
        }

        String scheme = trimmed.substring(0, separator);
        if (!BEARER_SCHEME.equalsIgnoreCase(scheme)) {
            return Resolution.missing();
        }

        String token = trimmed.substring(separator + 1).trim();
        if (token.isBlank() || token.length() > MAX_TOKEN_LENGTH || hasWhitespace(token)) {
            return Resolution.invalidCredentials();
        }

        return Resolution.found(token);
    }

    private boolean hasWhitespace(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public record Resolution(String token, boolean credentialsFound, boolean invalid) {
        static Resolution missing() {
            return new Resolution(null, false, false);
        }

        static Resolution invalidCredentials() {
            return new Resolution(null, true, true);
        }

        static Resolution found(String token) {
            return new Resolution(token, true, false);
        }
    }
}
