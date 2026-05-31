package com.actilazion.ariesreportingproject.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
@EnableJpaRepositories(
        basePackages       = "com.actilazion.aries_reporting.repository.reporting",
        entityManagerFactoryRef = "reportingEntityManagerFactory",
        transactionManagerRef   = "reportingTransactionManager"
)
public class DataSourceConfig {
    // Primary
    @Primary
    @Bean(name = "reportingDataSource")
    @ConfigurationProperties("spring.datasource.reporting.hikari")
    public DataSource reportingDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(
                System.getProperty("spring.datasource.reporting.url",
                        "jdbc:postgresql://localhost:5433/aries_reporting_db"));
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(name = "reportingEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean reportingEntityManagerFactory(
            @Qualifier("reportingDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.actilazion.aries_reporting.entity.reporting");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaPropertyMap(jpaProperties("validate"));
        return em;
    }

    @Primary
    @Bean(name = "reportingTransactionManager")
    public PlatformTransactionManager reportingTransactionManager(
            @Qualifier("reportingEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    // Secondary
    @Bean(name = "transactionDataSource")
    public DataSource transactionDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(
                System.getProperty("spring.datasource.transaction.url",
                        "jdbc:postgresql://localhost:5432/aries_transaction_db"));
        config.setMaximumPoolSize(5);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    @Bean(name = "transactionEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean transactionEntityManagerFactory(
            @Qualifier("transactionDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.actilazion.aries_reporting.entity.transaction");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaPropertyMap(jpaProperties("none")); // Không ddl-auto trên DB người khác
        return em;
    }

    @Bean(name = "transactionTransactionManager")
    public PlatformTransactionManager transactionTransactionManager(
            @Qualifier("transactionEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    // JPA Properties
    private Map<String, Object> jpaProperties(String ddlAuto) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto",                ddlAuto);
        props.put("hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.format_sql",                  true);
        props.put("hibernate.jdbc.time_zone",              "UTC");
        return props;
    }
}
