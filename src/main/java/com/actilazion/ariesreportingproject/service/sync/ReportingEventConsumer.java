package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.actilazion.ariesreportingproject.entity.reporting.ProcessedEvent;
import com.actilazion.ariesreportingproject.repository.reporting.ProcessedEventRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingEventConsumer {
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final TransferCompletedMessageMapper mapper;

    @Transactional(transactionManager = "reportingTransactionManager")
    public void consumeTransferCompleted(TransferCompletedMessage message) {
        validate(message);

        if (processedEventRepository.existsByEventId(message.eventId())) {
            log.info("[SYNC] Duplicate eventId={} - skipped", message.eventId());
            return;
        }

        if (reportingTransactionRepository.existsByOriginalTxId(message.aggregateId())) {
            log.info("[SYNC] Transaction already projected originalTxId={} eventId={}",
                    message.aggregateId(), message.eventId());
            markProcessed(message);
            return;
        }

        reportingTransactionRepository.save(mapper.toReportingTransaction(message));
        markProcessed(message);
        log.info("[SYNC] Projected transfer eventId={} transactionId={}",
                message.eventId(), message.aggregateId());
    }

    private void validate(TransferCompletedMessage message) {
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(message.eventId(), "eventId is required");
        Objects.requireNonNull(message.payload(), "payload is required");
        Objects.requireNonNull(message.payload().transactionId(), "payload.transactionId is required");

        if (!TransferCompletedMessage.EVENT_TYPE.equals(message.eventType())) {
            throw new IllegalArgumentException("Unsupported eventType: " + message.eventType());
        }
        if (message.schemaVersion() != TransferCompletedMessage.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + message.schemaVersion());
        }
    }

    private void markProcessed(TransferCompletedMessage message) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(message.eventId())
                .eventType(message.eventType())
                .aggregateId(message.aggregateId())
                .schemaVersion(message.schemaVersion())
                .build());
    }
}
