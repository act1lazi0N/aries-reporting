package com.actilazion.ariesreportingproject.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryPropertiesTest {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDown() {
        validator = null;
    }

    @Test
    void defaults_areValid() {
        assertThat(validator.validate(new EmailDeliveryProperties())).isEmpty();
    }

    @Test
    void invalidValues_failFastValidation() {
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setWorkerCount(0);
        properties.setQueueCapacity(-1);
        properties.setLeaseDuration(Duration.ZERO);
        properties.setRetryBaseDelay(Duration.ofMinutes(2));
        properties.setRetryMaxDelay(Duration.ofMinutes(1));
        properties.setRateLimitKey(" ");

        Set<ConstraintViolation<EmailDeliveryProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains(
                        "must be greater than or equal to 1",
                        "must be greater than or equal to 0",
                        "lease-duration must be greater than zero",
                        "retry-max-delay must be greater than or equal to retry-base-delay",
                        "must not be blank");
    }
}
