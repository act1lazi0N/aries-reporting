package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlySnapshotRepository extends JpaRepository<MonthlySnapshot, UUID> {
    // Finds a monthly snapshot by account, year, and month.
    Optional<MonthlySnapshot> findByAccountIdAndYearAndMonth(
            UUID accountId, Short year, Short month);

    // Finds the latest snapshot before a target reporting month for opening balance carry-forward.
    @Query("""
        SELECT s FROM MonthlySnapshot s
        WHERE s.accountId = :accountId
          AND (s.year < :year OR (s.year = :year AND s.month < :month))
        ORDER BY s.year DESC, s.month DESC
        """)
    List<MonthlySnapshot> findLatestBeforeMonth(
            @Param("accountId") UUID accountId,
            @Param("year") Short year,
            @Param("month") Short month,
            Pageable pageable);

    // Checks whether a monthly snapshot already exists for an account, year, and month.
    boolean existsByAccountIdAndYearAndMonth(
            UUID accountId, Short year, Short month);

    // Retrieves finalised monthly snapshots for an account, ordered newest first.
    List<MonthlySnapshot> findAllByAccountIdAndIsFinalisedTrueOrderByYearDescMonthDesc(
            UUID accountId);

    // Finds the unfinalised monthly snapshot for an account.
    Optional<MonthlySnapshot> findByAccountIdAndIsFinalisedFalse(UUID accountId);

    // Finds accounts with transactions in the month but without a monthly snapshot.
    @Query("""
        SELECT DISTINCT t.fromAccountId
        FROM ReportingTransaction t
        WHERE t.createdAt >= :startOfMonth
        AND NOT EXISTS (
            SELECT 1 FROM MonthlySnapshot s
            WHERE s.accountId = t.fromAccountId
              AND s.year = :year AND s.month = :month
        )
        """)
    List<UUID> findAccountsWithoutSnapshotForMonth(
            @Param("startOfMonth") OffsetDateTime startOfMonth,
            @Param("year")         Short year,
            @Param("month")        Short month
    );
}
