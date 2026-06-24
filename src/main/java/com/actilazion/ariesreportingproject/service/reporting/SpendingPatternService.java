package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.dto.response.SpendingPatternResponse;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpendingPatternService {
    private final ReportingTransactionRepository reportingTransactionRepository;

    @Transactional(readOnly = true)
    public SpendingPatternResponse getSpendingPattern(UUID accountId, OffsetDateTime from, OffsetDateTime to) {
        // Spending per hours — 24 slots
        List<Object[]> hourlyRaw = reportingTransactionRepository
                .findSpendingByHour(accountId, from, to);
        List<SpendingPatternResponse.HourlySlot> hourlySlots = new ArrayList<>();

        // Init 24 slots with 0
        long[] counts = new long[24];
        double[] volumes = new double[24];

        for (Object[] row : hourlyRaw) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            double volumne = ((Number) row[2]).doubleValue();
            counts[hour] = count;
            volumes[hour] = volumne;
        }

        for (int h = 0; h < 24; h++) {
            hourlySlots.add(new SpendingPatternResponse.HourlySlot(h, counts[h], volumes[h]));
        }

        // Peak hour - hour has the highest volume
        int peakHour = 0;
        double maxVol = 0;
        for (int h = 0; h < 24; h++) {
            if (volumes[h] > maxVol) {
                maxVol   = volumes[h];
                peakHour = h;
            }
        }

        return SpendingPatternResponse.builder()
                .accountId(accountId)
                .periodFrom(from.toLocalDate().toString())
                .periodTo(to.toLocalDate().toString())
                .hourlySpending(hourlySlots)
                .peakHour(peakHour)
                .build();
    }
}
