package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.DailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailySnapshotRepository extends JpaRepository<DailySnapshot, UUID> {
    // Finds a daily snapshot by account and snapshot date.
    Optional<DailySnapshot> findByAccountIdAndSnapshotDate(
            UUID accountId, LocalDate snapshotDate);

    // Finds the latest snapshot before a target business day for opening balance carry-forward.
    Optional<DailySnapshot> findFirstByAccountIdAndSnapshotDateBeforeOrderBySnapshotDateDesc(
            UUID accountId, LocalDate snapshotDate);

    // Checks whether a daily snapshot already exists for an account and snapshot date.
    boolean existsByAccountIdAndSnapshotDate(
            UUID accountId, LocalDate snapshotDate);

    // Retrieves daily snapshots for an account within a date range, ordered by snapshot date ascending.
    List<DailySnapshot> findAllByAccountIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID accountId, LocalDate from, LocalDate to);
}
