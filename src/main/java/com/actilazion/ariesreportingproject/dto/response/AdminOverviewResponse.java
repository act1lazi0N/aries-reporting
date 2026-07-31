package com.actilazion.ariesreportingproject.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record AdminOverviewResponse(
        String periodFrom,
        String periodTo,
        long totalTransactions,
        BigDecimal totalVolume,
        long completedCount,
        long failedCount,
        long pendingCount,
        double failureRate,
        List<DailyVolume> dailyVolumes
) {
    public record DailyVolume(
            String day,
            long txCount,
            BigDecimal volume
    ) {
    }
}
