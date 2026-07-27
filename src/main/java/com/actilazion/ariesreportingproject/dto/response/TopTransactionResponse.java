package com.actilazion.ariesreportingproject.dto.response;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TopTransactionResponse(
        UUID id,
        UUID originalTxId,
        String fromAccountNumber,
        String toAccountNumber,
        String fromOwnerName,
        String toOwnerName,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        OffsetDateTime createdAt
) {
    public static TopTransactionResponse from(ReportingTransaction tx) {
        return new TopTransactionResponse(
                tx.getId(),
                tx.getOriginalTxId(),
                tx.getFromAccountNumber(),
                tx.getToAccountNumber(),
                tx.getFromOwnerName(),
                tx.getToOwnerName(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }
}
