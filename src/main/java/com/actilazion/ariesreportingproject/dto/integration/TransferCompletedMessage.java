package com.actilazion.ariesreportingproject.dto.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferCompletedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public static final String EVENT_TYPE = "transaction.transfer.completed";
    public static final int SCHEMA_VERSION = 1;

    public UUID aggregateId() {
        return payload.transactionId();
    }

    public record Payload(
            UUID transactionId,
            UUID fromAccountId,
            UUID toAccountId,
            UUID initiatedBy,
            String fromOwnerName,
            String toOwnerName,
            String fromAccountNumber,
            String toAccountNumber,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }
}
