package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.event.TransferCompletedEvent;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for TransferCompletedEvent and persists it to the reporting DB.
 *
 * @Async: runs on a separate thread (exportTaskExecutor), so it does not block
 *   the transfer system's money transfer flow. If this listener fails, the
 *   original transaction has already been committed and is not affected.
 *
 * @Transactional: uses reportingTransactionManager to write to the reporting DB,
 *   not the transfer DB.
 *
 * Idempotency: checks existsByOriginalTxId before inserting, so duplicate events
 *   caused by restarts or retries do not create duplicate rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingSyncListener {
    private final ReportingTransactionRepository reportingTransactionRepository;

    @Async("exportTaskExecutor")
    @EventListener
    @Transactional(transactionManager = "reportingTransactionManager")
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.debug("[SYNC] Received event for transactionId={}", event.transactionId());

        // Idempotency check: avoid duplicate inserts when the same event is delivered more than once.
        if (reportingTransactionRepository.existsByOriginalTxId(event.transactionId())) {
            log.warn("[SYNC] Duplicate event for transactionId={} - skipped", event.transactionId());
            return;
        }

        try {
            ReportingTransaction transaction = mapToReportingTransaction(event);
            reportingTransactionRepository.save(transaction);
            log.info("[SYNC] Persisted event for transactionId={}", event.transactionId());
        } catch (Exception ex) {
            log.error("[SYNC] Failed to persist event for transactionId={} - {}", event.transactionId(), ex.getMessage(), ex);
        }
    }

    private ReportingTransaction mapToReportingTransaction(
            TransferCompletedEvent event) {

        // Store dayOfWeek and hourOfDay during sync to avoid computing them during queries.
        short dayOfWeek = (short) event.createdAt()
                .getDayOfWeek()
                .getValue();

        short hourOfDay = (short) event.createdAt().getHour();

        return ReportingTransaction.builder()
                .originalTxId(event.transactionId())
                .fromAccountId(event.fromAccountId())
                .toAccountId(event.toAccountId())
                .fromOwnerName(event.fromOwnerName())
                .toOwnerName(event.toOwnerName())
                .fromAccountNumber(event.fromAccountNumber())
                .toAccountNumber(event.toAccountNumber())
                .amount(event.amount())
                .currency(event.currency())
                .status(event.status())
                .description(event.description())
                .dayOfWeek(dayOfWeek)
                .hourOfDay(hourOfDay)
                .createdAt(event.createdAt())
                .completedAt(event.completedAt())
                .build();
    }
}
