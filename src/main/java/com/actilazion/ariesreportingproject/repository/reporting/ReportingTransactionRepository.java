package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportingTransactionRepository extends JpaRepository<ReportingTransaction, UUID> {
    Optional<ReportingTransaction> findByOriginalTxId(UUID originalTxId);

    boolean existsByOriginalTxId(UUID originalTxId);

    @Query("""
        SELECT t FROM ReportingTransaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.createdAt >= :from
          AND t.createdAt < :to
        ORDER BY t.createdAt DESC
        """)
    Page<ReportingTransaction> findByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.fromAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        """)
    BigDecimal sumDebitByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.toAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        """)
    BigDecimal sumCreditByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT COUNT(t)
        FROM ReportingTransaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.createdAt >= :from
          AND t.createdAt < :to
        """)
    long countByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT t FROM ReportingTransaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.status = 'COMPLETED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        ORDER BY t.amount DESC
        """)
    List<ReportingTransaction> findTopByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Query("""
        SELECT t.hourOfDay, COUNT(t), COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.fromAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        GROUP BY t.hourOfDay
        ORDER BY t.hourOfDay
        """)
    List<Object[]> findSpendingByHour(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query(value = """
        SELECT
            DATE(created_at) as day,
            COUNT(*) as tx_count,
            SUM(amount) as total_volume
        FROM reporting_transactions
        WHERE status = 'COMPLETED'
          AND created_at >= :from
          AND created_at < :to
        GROUP BY DATE(created_at)
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> findDailyVolumeByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT t.status, COUNT(t)
        FROM ReportingTransaction t
        WHERE t.createdAt >= :from
          AND t.createdAt < :to
        GROUP BY t.status
        """)
    List<Object[]> countByStatusAndPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    long countByCreatedAtAfter(OffsetDateTime after);

    @Query("""
        SELECT t.originalTxId FROM ReportingTransaction t
        WHERE t.createdAt > :after
        """)
    List<UUID> findSyncedTxIdsByCreatedAtAfter(@Param("after") OffsetDateTime after);

    @Query("""
        SELECT t.originalTxId FROM ReportingTransaction t
        WHERE t.createdAt >= :from
          AND t.createdAt < :to
        """)
    List<UUID> findSyncedTxIdsByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT DISTINCT t.fromAccountId
        FROM ReportingTransaction t
        WHERE t.createdAt >= :from
          AND t.createdAt < :to
        """)
    List<UUID> findDistinctFromAccountIdsByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT DISTINCT t.toAccountId
        FROM ReportingTransaction t
        WHERE t.createdAt >= :from
          AND t.createdAt < :to
        """)
    List<UUID> findDistinctToAccountIdsByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT t.hourOfDay, COUNT(t)
        FROM ReportingTransaction t
        WHERE t.status = 'FAILED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        GROUP BY t.hourOfDay
        ORDER BY t.hourOfDay
        """)
    List<Object[]> countFailedByHour(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
        SELECT t FROM ReportingTransaction t
        WHERE t.status = 'FAILED'
          AND t.createdAt >= :from
          AND t.createdAt < :to
        ORDER BY t.createdAt DESC
        """)
    Page<ReportingTransaction> findFailedByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );
}
