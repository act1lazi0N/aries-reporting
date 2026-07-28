package com.actilazion.ariesreportingproject.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.reporting")
public class AppProperties {
    private String exportDir = "/tmp/aries-exports";
    private int exportTtlHours = 24;
    private String mailFrom = "noreply@aries.local";
    private String publicBaseUrl = "http://localhost:3000";
    private int cacheTtlMinutes = 10;

}
