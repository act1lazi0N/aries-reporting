package com.actilazion.ariesreportingproject.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.reporting.email")
public class EmailDeliveryProperties {
    @Min(1)
    private int activeUserPageSize = 100;
    @Min(1)
    private int dispatchBatchSize = 20;
    @Min(1)
    private int workerCount = 2;
    @PositiveOrZero
    private int queueCapacity = 20;
    @PositiveOrZero
    private long dispatchDelayMs = 1000;
    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(5);
    @Min(1)
    private int maxAttempts = 5;
    @NotNull
    private Duration retryBaseDelay = Duration.ofSeconds(30);
    @NotNull
    private Duration retryMaxDelay = Duration.ofHours(1);
    @NotNull
    private Duration rateLimitRetryDelay = Duration.ofMillis(250);
    @NotNull
    private Duration emptyStatementGracePeriod = Duration.ofHours(6);
    @Positive
    private double permitsPerSecond = 10.0;
    @Min(1)
    private int burstCapacity = 10;
    @NotBlank
    private String rateLimitKey = "aries:email:rate:smtp";

    @AssertTrue(message = "lease-duration must be greater than zero")
    public boolean isLeaseDurationPositive() {
        return leaseDuration != null && !leaseDuration.isZero() && !leaseDuration.isNegative();
    }

    @AssertTrue(message = "retry delays must be greater than zero")
    public boolean areRetryDelaysPositive() {
        return retryBaseDelay != null && !retryBaseDelay.isZero() && !retryBaseDelay.isNegative()
                && retryMaxDelay != null && !retryMaxDelay.isZero() && !retryMaxDelay.isNegative();
    }

    @AssertTrue(message = "retry-max-delay must be greater than or equal to retry-base-delay")
    public boolean isRetryMaxDelayValid() {
        return retryBaseDelay == null || retryMaxDelay == null
                || !retryMaxDelay.minus(retryBaseDelay).isNegative();
    }

    @AssertTrue(message = "rate-limit-retry-delay must not be negative")
    public boolean isRateLimitRetryDelayValid() {
        return rateLimitRetryDelay != null && !rateLimitRetryDelay.isNegative();
    }

    @AssertTrue(message = "empty-statement-grace-period must be greater than zero")
    public boolean isEmptyStatementGracePeriodPositive() {
        return emptyStatementGracePeriod != null
                && !emptyStatementGracePeriod.isZero()
                && !emptyStatementGracePeriod.isNegative();
    }
}
