package com.actilazion.ariesreportingproject.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.reporting.email")
public class EmailDeliveryProperties {
    private int activeUserPageSize = 100;
    private int dispatchBatchSize = 20;
    private int workerCount = 2;
    private int queueCapacity = 20;
    private long dispatchDelayMs = 1000;
    private Duration leaseDuration = Duration.ofMinutes(5);
    private int maxAttempts = 5;
    private Duration retryBaseDelay = Duration.ofSeconds(30);
    private Duration retryMaxDelay = Duration.ofHours(1);
    private Duration rateLimitRetryDelay = Duration.ofMillis(250);
    private double permitsPerSecond = 10.0;
    private int burstCapacity = 10;
    private String rateLimitKey = "aries:email:rate:smtp";
}
