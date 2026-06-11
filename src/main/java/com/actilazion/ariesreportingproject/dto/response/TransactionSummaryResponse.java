package com.actilazion.ariesreportingproject.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TransactionSummaryResponse(
        UUID accountId,
        int year,
        int month,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        int txCount,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        boolean isFromSnapshot
) {
}
