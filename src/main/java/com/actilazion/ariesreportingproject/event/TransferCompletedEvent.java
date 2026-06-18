package com.actilazion.ariesreportingproject.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferCompletedEvent(
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
