package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.event.TransferCompletedEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class TransferCompletedMessageMapper {
    public TransferCompletedMessage fromLegacyEvent(TransferCompletedEvent event) {
        return new TransferCompletedMessage(
                deterministicLegacyEventId(event.transactionId()),
                TransferCompletedMessage.EVENT_TYPE,
                TransferCompletedMessage.SCHEMA_VERSION,
                event.completedAt() != null ? event.completedAt() : event.createdAt(),
                new TransferCompletedMessage.Payload(
                        event.transactionId(),
                        event.fromAccountId(),
                        event.toAccountId(),
                        event.initiatedBy(),
                        event.fromOwnerName(),
                        event.toOwnerName(),
                        event.fromAccountNumber(),
                        event.toAccountNumber(),
                        event.amount(),
                        event.currency(),
                        event.status(),
                        event.description(),
                        event.createdAt(),
                        event.completedAt()
                )
        );
    }

    public ReportingTransaction toReportingTransaction(TransferCompletedMessage message) {
        TransferCompletedMessage.Payload payload = message.payload();
        OffsetDateTime createdAt = payload.createdAt();
        short dayOfWeek = (short) createdAt.getDayOfWeek().getValue();
        short hourOfDay = (short) createdAt.getHour();

        return ReportingTransaction.builder()
                .originalTxId(payload.transactionId())
                .fromAccountId(payload.fromAccountId())
                .toAccountId(payload.toAccountId())
                .fromOwnerName(payload.fromOwnerName())
                .toOwnerName(payload.toOwnerName())
                .fromAccountNumber(payload.fromAccountNumber())
                .toAccountNumber(payload.toAccountNumber())
                .amount(payload.amount())
                .currency(payload.currency())
                .status(payload.status())
                .description(payload.description())
                .dayOfWeek(dayOfWeek)
                .hourOfDay(hourOfDay)
                .createdAt(createdAt)
                .completedAt(payload.completedAt())
                .build();
    }

    private UUID deterministicLegacyEventId(UUID transactionId) {
        return UUID.nameUUIDFromBytes(
                ("legacy-transfer-completed:" + transactionId)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
