package com.actilazion.ariesreportingproject.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record SpendingPatternResponse(
        UUID accountId,
        String periodFrom,
        String periodTo,
        List<HourlySlot> hourlySlots,
        int peakHour
) {
    public record HourlySlot(
            int    hour,
            long   txCount,
            double volume
    ) {
    }
}
