package com.actilazion.ariesreportingproject.dto.response;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AccountStatementResponse(
        UUID accountId,
        String periodFrom,
        String periodTo,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netFlow,
        int txCount,
        Page<TransactionLine> transactions
) {
    public record TransactionLine(
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
    }
}
