package com.actilazion.ariesreportingproject.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record FailedTransactionReportResponse(
        String periodFrom,
        String periodTo,
        long totalTransactions,
        long failedCount,
        long completedCount,
        long pendingCount,
        double failureRate,
        List<HourlyFail> failuresByHour,
        List<StatusBreakdown> breakdown
) {
    public record HourlyFail(
            int hour,
            long failedCount,
            double failRateAtHour
    ) {
    }

    public record StatusBreakdown(
            String status,
            long count,
            double percentage
    ) {
    }
}
