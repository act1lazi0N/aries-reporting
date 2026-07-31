package com.actilazion.ariesreportingproject.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {
    private final Environment environment;

    public DataSourceConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean(name = "reportingDataSourceProperties")
    @ConfigurationProperties("spring.datasource.reporting")
    public DataSourceProperties reportingDataSourceProperties() {
        return new DataSourceProperties();
    }

    // Primary
    @Primary
    @Bean(name = "reportingDataSource")
    @ConfigurationProperties("spring.datasource.reporting.hikari")
    public DataSource reportingDataSource(
            @Qualifier("reportingDataSourceProperties")
            DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "reportingEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean reportingEntityManagerFactory(
            @Qualifier("reportingDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.actilazion.ariesreportingproject.entity.reporting");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaPropertyMap(jpaProperties("validate", true));
        return em;
    }

    @Primary
    @Bean(name = "reportingTransactionManager")
    public PlatformTransactionManager reportingTransactionManager(
            @Qualifier("reportingEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    @Bean(name = "transactionDataSourceProperties")
    @ConfigurationProperties("spring.datasource.transaction")
    public DataSourceProperties transactionDataSourceProperties() {
        return new DataSourceProperties();
    }

    // Secondary
    @Bean(name = "transactionDataSource")
    @ConfigurationProperties("spring.datasource.transaction.hikari")
    public DataSource transactionDataSource(
            @Qualifier("transactionDataSourceProperties")
            DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setReadOnly(true);
        return dataSource;
    }

    @Bean(name = "transactionEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean transactionEntityManagerFactory(
            @Qualifier("transactionDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.actilazion.ariesreportingproject.entity.transaction");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaPropertyMap(jpaProperties("none", false)); // Do not ddl-auto on other user DB
        return em;
    }

    @Bean(name = "transactionTransactionManager")
    public PlatformTransactionManager transactionTransactionManager(
            @Qualifier("transactionEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    // JPA Properties
    private Map<String, Object> jpaProperties(String ddlAuto, boolean allowDdlOverride) {
        Map<String, Object> props = new HashMap<>();
        String resolvedDdlAuto = allowDdlOverride
                ? environment.getProperty("spring.jpa.hibernate.ddl-auto", ddlAuto)
                : ddlAuto;
        props.put("hibernate.hbm2ddl.auto", resolvedDdlAuto);
        props.put("hibernate.dialect",
                environment.getProperty(
                        "spring.jpa.properties.hibernate.dialect",
                        "org.hibernate.dialect.PostgreSQLDialect"));
        props.put("hibernate.format_sql", true);
        props.put("hibernate.jdbc.time_zone", "UTC");
        return props;
    }
}
