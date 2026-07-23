package com.actilazion.ariesreportingproject.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenResolverTest {
    private final BearerTokenResolver resolver = new BearerTokenResolver();

    @Test
    @DisplayName("resolve: missing authorization header is not credentials")
    void resolve_missingHeader_returnsMissing() {
        var result = resolver.resolve(new MockHttpServletRequest());

        assertThat(result.credentialsFound()).isFalse();
        assertThat(result.invalid()).isFalse();
    }

    @Test
    @DisplayName("resolve: unsupported scheme is not JWT credentials")
    void resolve_unsupportedScheme_returnsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");

        var result = resolver.resolve(request);

        assertThat(result.credentialsFound()).isFalse();
        assertThat(result.invalid()).isFalse();
    }

    @Test
    @DisplayName("resolve: duplicate authorization headers are invalid")
    void resolve_duplicateHeaders_invalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer one");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer two");

        var result = resolver.resolve(request);

        assertThat(result.credentialsFound()).isTrue();
        assertThat(result.invalid()).isTrue();
    }

    @Test
    @DisplayName("resolve: empty bearer token is invalid")
    void resolve_emptyBearer_invalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        var result = resolver.resolve(request);

        assertThat(result.credentialsFound()).isTrue();
        assertThat(result.invalid()).isTrue();
    }

    @Test
    @DisplayName("resolve: bearer scheme is case-insensitive")
    void resolve_lowercaseBearer_returnsToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer abc.def.sig");

        var result = resolver.resolve(request);

        assertThat(result.credentialsFound()).isTrue();
        assertThat(result.invalid()).isFalse();
        assertThat(result.token()).isEqualTo("abc.def.sig");
    }
}
