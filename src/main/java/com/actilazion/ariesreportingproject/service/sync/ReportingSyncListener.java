package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.event.TransferCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Legacy in-process adapter for TransferCompletedEvent.
 *
 * Durable cross-service delivery should enter through the same
 * ReportingEventConsumer contract from a broker/outbox adapter. This listener is
 * kept only as a compatibility adapter for local Spring event publishers.
 *
 * @deprecated Use a durable broker/outbox adapter that calls
 * {@link ReportingEventConsumer#consumeTransferCompleted(
 * com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage)}.
 */
@Deprecated(since = "0.0.1", forRemoval = false)
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingSyncListener {
    private final TransferCompletedMessageMapper mapper;
    private final ReportingEventConsumer reportingEventConsumer;

    @Async("exportTaskExecutor")
    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.debug("[SYNC] Received event for transactionId={}", event.transactionId());
        try {
            reportingEventConsumer.consumeTransferCompleted(mapper.fromLegacyEvent(event));
        } catch (Exception ex) {
            log.error("[SYNC] Failed to consume event for transactionId={} - {}",
                    event.transactionId(), ex.getMessage(), ex);
        }
    }
}
