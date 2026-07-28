package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.TransactionViewRepository;
import com.actilazion.ariesreportingproject.service.sync.BackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*
 * Compare the transaction counts between the transfer DB and reporting DB every night.
 * Detect missed events, for example when ReportingSyncListener fails
 * and records are not backfilled.
 *
 * If a mismatch is detected, log a warning and automatically trigger backfill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {
    private final TransactionViewRepository transactionViewRepository;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final BackfillService backfillService;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runReconciliation() {
        OffsetDateTime since = OffsetDateTime.now().minusHours(25);
        log.info("[RECON] Starting reconciliation since={}", since);

        Set<UUID> sourceIds = new HashSet<>(transactionViewRepository.findIdsByCreatedAtAfter(since));
        Set<UUID> reportingIds = new HashSet<>(reportingTransactionRepository.findSyncedTxIdsByCreatedAtAfter(since));
        long sourceCount = sourceIds.size();
        long reportingCount = reportingIds.size();

        log.info("[RECON] Source count={}, reporting count={}", sourceCount, reportingCount);

        Set<UUID> missingIds = new HashSet<>(sourceIds);
        missingIds.removeAll(reportingIds);

        Set<UUID> phantomIds = new HashSet<>(reportingIds);
        phantomIds.removeAll(sourceIds);

        if (!missingIds.isEmpty()) {
            log.warn("[RECON] Missing reporting transactions detected. missing={} phantom={} - triggering backfill",
                    missingIds.size(), phantomIds.size());

            long synced = backfillService.backfill(since);
            log.info("[RECON] Backfill completed, synced={}", synced);
        } else if (!phantomIds.isEmpty()) {
            log.warn("[RECON] Reporting has {} phantom records. Manual investigation required", phantomIds.size());
        } else {
            log.info("[RECON] No mismatch detected");
        }
    }
}
