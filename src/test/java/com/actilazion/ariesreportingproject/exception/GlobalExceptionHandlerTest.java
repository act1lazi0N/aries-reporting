package com.actilazion.ariesreportingproject.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleBadRequest: maps invalid client input to 400")
    void handleBadRequest_mapsIllegalArgumentTo400() {
        var response = handler.handleBadRequest(
                new IllegalArgumentException("from must be before or equal to to"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("from must be before or equal to to");
    }
}
