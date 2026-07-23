package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.dto.response.SpendingPatternResponse;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        BigDecimal[] volumes = new BigDecimal[24];
        for (int h = 0; h < 24; h++) {
            volumes[h] = BigDecimal.ZERO;
        }

        for (Object[] row : hourlyRaw) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            BigDecimal volume = toBigDecimal(row[2]);
            counts[hour] = count;
            volumes[hour] = volume;
        }

        for (int h = 0; h < 24; h++) {
            hourlySlots.add(new SpendingPatternResponse.HourlySlot(h, counts[h], volumes[h]));
        }

        // Peak hour - hour has the highest volume
        int peakHour = 0;
        BigDecimal maxVol = BigDecimal.ZERO;
        for (int h = 0; h < 24; h++) {
            if (volumes[h].compareTo(maxVol) > 0) {
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

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}
