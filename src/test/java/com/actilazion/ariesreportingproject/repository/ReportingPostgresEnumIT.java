package com.actilazion.ariesreportingproject.repository;

import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.enums.ReportJobType;
import com.actilazion.ariesreportingproject.repository.reporting.EmailLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReportingPostgresEnumIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HikariDataSource dataSource;
    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.actilazion.ariesreportingproject.entity.reporting");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.jdbc.time_zone", "UTC"));
        factory.afterPropertiesSet();
        entityManagerFactory = factory.getObject();
    }

    @AfterAll
    static void tearDown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void nativeEnums_supportPersistAndJpqlParameters() {
        UUID requestedBy = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String billingMonth = "2026-07";
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            entityManager.getTransaction().begin();
            entityManager.persist(ReportJob.builder()
                    .requestedBy(requestedBy)
                    .jobType(ReportJobType.ACCOUNT_STATEMENT)
                    .status(ReportJobStatus.READY)
                    .format(ReportFormat.PDF)
                    .params(Map.of("accountId", UUID.randomUUID().toString()))
                    .build());
            entityManager.persist(EmailLog.builder()
                    .userId(userId)
                    .billingMonth(billingMonth)
                    .idempotencyKey(EmailLog.buildIdempotencyKey(userId, billingMonth))
                    .status(EmailStatus.SENDING)
                    .build());
            entityManager.getTransaction().commit();

            assertThat(entityManager.createQuery(
                    "select j from ReportJob j where j.status = :status", ReportJob.class)
                    .setParameter("status", ReportJobStatus.READY)
                    .getResultList()).hasSize(1);
            assertThat(entityManager.createQuery(
                    "select e from EmailLog e where e.status = :status", EmailLog.class)
                    .setParameter("status", EmailStatus.SENDING)
                    .getResultList()).hasSize(1);
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    @Test
    void nativeClaimQuery_supportsPostgresSkipLockedWithoutJpaLockHint() {
        UUID userId = UUID.randomUUID();
        String billingMonth = "2026-08";
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EmailLogRepository repository = new JpaRepositoryFactory(entityManager)
                .getRepository(EmailLogRepository.class);

        try {
            entityManager.getTransaction().begin();
            EmailLog emailLog = EmailLog.builder()
                    .userId(userId)
                    .billingMonth(billingMonth)
                    .idempotencyKey(EmailLog.buildIdempotencyKey(userId, billingMonth))
                    .status(EmailStatus.PENDING)
                    .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                    .build();
            entityManager.persist(emailLog);
            entityManager.getTransaction().commit();

            entityManager.getTransaction().begin();
            assertThat(repository.findDueForUpdate(OffsetDateTime.now(ZoneOffset.UTC), 10))
                    .extracting(EmailLog::getId)
                    .containsExactly(emailLog.getId());
            entityManager.getTransaction().rollback();
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }
}
