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
    // Finds a reporting transaction by its original transaction id.
    Optional<ReportingTransaction> findByOriginalTxId(UUID originalTxId);

    // Checks whether a reporting transaction already exists by original transaction id.
    boolean existsByOriginalTxId(UUID originalTxId);

    // Retrieves transactions related to an account within a time period, ordered newest first with pagination.
    @Query("""
        SELECT t FROM ReportingTransaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
        """)
    Page<ReportingTransaction> findByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to,
            Pageable pageable
    );

    // Calculates the total debit amount for an account in a time period using COMPLETED transactions.
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.fromAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumDebitByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to
    );

    // Calculates the total credit amount for an account in a time period using COMPLETED transactions.
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.toAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumCreditByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to
    );

    // Retrieves the largest COMPLETED transactions for an account in a time period based on the Pageable limit.
    @Query("""
        SELECT t FROM ReportingTransaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.status = 'COMPLETED'
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.amount DESC
        """)
    List<ReportingTransaction> findTopByAccountAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to,
            Pageable pageable  // PageRequest.of(0, 5) to get the top 5.
    );

    // Aggregates transaction count and total spending by hour for an account.
    @Query("""
        SELECT t.hourOfDay, COUNT(t), COALESCE(SUM(t.amount), 0)
        FROM ReportingTransaction t
        WHERE t.fromAccountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.hourOfDay
        ORDER BY t.hourOfDay
        """)
    List<Object[]> findSpendingByHour(
            @Param("accountId") UUID accountId,
            @Param("from")      OffsetDateTime from,
            @Param("to")        OffsetDateTime to
    );

    // Aggregates daily transaction count and total volume within a time period.
    @Query(value = """
        SELECT 
            DATE(created_at) as day,
            COUNT(*) as tx_count,
            SUM(amount) as total_volume
        FROM reporting_transactions
        WHERE status = 'COMPLETED'
          AND created_at BETWEEN :from AND :to
        GROUP BY DATE(created_at)
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> findDailyVolumeByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to")   OffsetDateTime to
    );

    // Counts transactions by status within a time period.
    @Query("""
        SELECT t.status, COUNT(t)
        FROM ReportingTransaction t
        WHERE t.createdAt BETWEEN :from AND :to
        GROUP BY t.status
        """)
    List<Object[]> countByStatusAndPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to")   OffsetDateTime to
    );

    // Counts transactions created after the specified time.
    long countByCreatedAtAfter(OffsetDateTime after);

    // Retrieves original transaction ids that were synced within a time period.
    @Query("""
        SELECT t.originalTxId FROM ReportingTransaction t
        WHERE t.createdAt BETWEEN :from AND :to
        """)
    List<UUID> findSyncedTxIdsByPeriod(
            @Param("from") OffsetDateTime from,
            @Param("to")   OffsetDateTime to
    );
}
