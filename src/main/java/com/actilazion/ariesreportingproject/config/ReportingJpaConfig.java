package com.actilazion.ariesreportingproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages            = "com.actilazion.ariesreportingproject.repository.reporting",
        entityManagerFactoryRef = "reportingEntityManagerFactory",
        transactionManagerRef   = "reportingTransactionManager"
)
public class ReportingJpaConfig {
}
