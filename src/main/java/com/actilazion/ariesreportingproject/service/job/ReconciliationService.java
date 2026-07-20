package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.TransactionViewRepository;
import com.actilazion.ariesreportingproject.service.sync.BackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

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

        long sourceCount = transactionViewRepository.countByCreatedAtAfter(since);
        long reportingCount = reportingTransactionRepository.countByCreatedAtAfter(since);

        log.info("[RECON] Source count={}, reporting count={}", sourceCount, reportingCount);

        if (sourceCount > reportingCount) {
            long diff = sourceCount - reportingCount;
            log.warn("[RECON] MISMATCH detected. diff={} - triggering backfill", diff);

            // Backfill the mismatch automatically.
            long synced = backfillService.backfill(since);
            log.info("[RECON] Backfill completed, synced={}", synced);
        } else if (reportingCount > sourceCount) {
            long diff = reportingCount - sourceCount;
            log.warn("[RECON] Reporting exceeds source by {} records. Manual investigation required", diff);
        } else {
            log.info("[RECON] No mismatch detected");
        }
    }
}
