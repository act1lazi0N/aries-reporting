package com.actilazion.ariesreportingproject.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private String issuer = "aries-transaction";
    private String audience = "aries-transaction-api";
    private String tokenType = "access";
    private long clockSkewSeconds = 30;
}
