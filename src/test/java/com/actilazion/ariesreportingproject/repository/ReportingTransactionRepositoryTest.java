package com.actilazion.ariesreportingproject.repository;


import com.actilazion.ariesreportingproject.support.ApplicationTestPropertiesInitializer;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = ApplicationTestPropertiesInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportingTransactionRepositoryTest {
    @Autowired
    TestEntityManager em;
    @Autowired
    ReportingTransactionRepository repo;

    private UUID accountA;
    private UUID accountB;
    private UUID accountC;
    @BeforeEach
    void setUp() {
        accountA = UUID.randomUUID();
        accountB = UUID.randomUUID();
        accountC = UUID.randomUUID();

        // A -> B: 1,000,000
        persist(accountA, accountB, new BigDecimal("1000000"), "COMPLETED",
                OffsetDateTime.now().minusDays(2));

        // B -> A: 500,000
        persist(accountB, accountA, new BigDecimal("500000"), "COMPLETED",
                OffsetDateTime.now().minusDays(1));

        // A -> C: 2,000,000
        persist(accountA, accountC, new BigDecimal("2000000"), "COMPLETED",
                OffsetDateTime.now().minusHours(3));

        // Failed
        persist(accountA, accountB, new BigDecimal("999999"), "FAILED",
                OffsetDateTime.now().minusHours(1));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByAccountAndPeriod: return both send and receive")
    void findByAccountAndPeriod_returnsBothDirections() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        Page<ReportingTransaction> result = repo.findByAccountAndPeriod(
                accountA, from, to, PageRequest.of(0, 10));

        // A sent 2 COMPLETED + 1 FAILED + received 1 = 4
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("sumDebitByAccountAndPeriod: only count COMPLETED")
    void sumDebit_onlyCompleted() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        BigDecimal debit = repo.sumDebitByAccountAndPeriod(accountA, from, to);

        // 1,000,000 + 2,000,000 = 3,000,000 (does not count FAILED)
        assertThat(debit).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("sumCreditByAccountAndPeriod: only count when toAccount")
    void sumCredit_onlyToAccount() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        BigDecimal credit = repo.sumCreditByAccountAndPeriod(accountA, from, to);

        // Only B -> A: 500,000
        assertThat(credit).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("findTopByAccountAndPeriod: amount DESC")
    void findTop_sortedByAmountDesc() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        List<ReportingTransaction> top = repo.findTopByAccountAndPeriod(
                accountA, from, to, PageRequest.of(0, 2));

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getAmount())
                .isGreaterThanOrEqualTo(top.get(1).getAmount());
    }

    @Test
    @DisplayName("existsByOriginalTxId: idempotency check")
    void existsByOriginalTxId() {
        UUID existingTxId = repo.findAll().get(0).getOriginalTxId();
        assertThat(repo.existsByOriginalTxId(existingTxId)).isTrue();
        assertThat(repo.existsByOriginalTxId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("countByCreatedAtAfter: use for reconciliation")
    void countByCreatedAtAfter() {
        long count = repo.countByCreatedAtAfter(
                OffsetDateTime.now().minusDays(1).minusHours(1));
        // B -> A (a day ago) + A -> C (3 hours ago) + FAILED (an hour ago) = 3
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countByAccountAndPeriod: counts both directions and all statuses")
    void countByAccountAndPeriod_countsBothDirectionsAndAllStatuses() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        long count = repo.countByAccountAndPeriod(accountA, from, to);

        // A sent 2 COMPLETED + 1 FAILED + received 1 COMPLETED = 4
        assertThat(count).isEqualTo(4);
    }

    // Helper
    private void persist(UUID from, UUID to, BigDecimal amount,
                         String status, OffsetDateTime createdAt) {
        em.persist(ReportingTransaction.builder()
                .originalTxId(UUID.randomUUID())
                .fromAccountId(from)
                .toAccountId(to)
                .fromOwnerName("User " + from.toString().substring(0, 4))
                .toOwnerName("User " + to.toString().substring(0, 4))
                .fromAccountNumber("ACC" + from.toString().substring(0, 6))
                .toAccountNumber("ACC" + to.toString().substring(0, 6))
                .amount(amount)
                .currency("VND")
                .status(status)
                .dayOfWeek((short) createdAt.getDayOfWeek().getValue())
                .hourOfDay((short) createdAt.getHour())
                .createdAt(createdAt)
                .build());
    }

}
