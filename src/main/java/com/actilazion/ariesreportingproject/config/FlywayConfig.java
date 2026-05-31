package com.actilazion.ariesreportingproject.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {
    @Bean(initMethod = "migrate")
    public Flyway reportingFlyway(
            @Qualifier("reportingDataSource") DataSource reportingDataSource) {

        return Flyway.configure()
                .dataSource(reportingDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .table("flyway_schema_history")
                .load();
    }
}
