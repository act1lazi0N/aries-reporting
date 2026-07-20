package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.dto.response.FailedTransactionReportResponse;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Returns: - Total number of transactions in the period, with a breakdown by status
 * - Failure rate
 * - Error distribution by hour of day (24 slots)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedTransactionReportService {
    private final ReportingTransactionRepository reportingTransactionRepository;

    @Transactional(readOnly = true)
    public FailedTransactionReportResponse getFailedReport(
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        log.debug("[F6] Building failed transaction report from={} to={}", from, to);

        // Statue breakdown
        List<Object[]> statusCounts = reportingTransactionRepository.countByStatusAndPeriod(from, to);
        long total = 0;
        long failed = 0;
        long completed = 0;
        long pending = 0;

        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case "FAILED" -> failed = count;
                case "COMPLETED" -> completed = count;
                case "PENDING" -> pending = count;
            }
        }

        double failureRate = total > 0
                ? Math.round((double) failed / total * 10000.0) / 100.0  // 2 decimal
                : 0.0;

        List<FailedTransactionReportResponse.StatusBreakdown> breakdown = new ArrayList<>();

        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            long count = ((Number) row[1]).longValue();
            double pct = total > 0
                    ? Math.round((double) count / total * 10000.0) / 100.0
                    : 0.0;
            breakdown.add(new FailedTransactionReportResponse.StatusBreakdown(
                    status, count, pct));
        }

        // Monthly failure distribution
        List<Object[]> hourlyRaw = reportingTransactionRepository.countFailedByHour(from, to);

        // Build 24-slot map
        long[] failedByHour = new long[24];
        for (Object[] row : hourlyRaw) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            failedByHour[hour] = count;
        }

        // Query total per hour for counting fail rate per hour
        List<FailedTransactionReportResponse.HourlyFail> hourlyFails = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            double rateAtHour = total > 0
                    ? Math.round((double) failedByHour[h] / total * 10000.0) / 100.0
                    : 0.0;
            hourlyFails.add(new FailedTransactionReportResponse.HourlyFail(
                    h, failedByHour[h], rateAtHour));
        }

        return FailedTransactionReportResponse.builder()
                .periodFrom(from.toLocalDate().toString())
                .periodTo(to.toLocalDate().toString())
                .totalTransactions(total)
                .failedCount(failed)
                .completedCount(completed)
                .pendingCount(pending)
                .failureRate(failureRate)
                .failuresByHour(hourlyFails)
                .breakdown(breakdown)
                .build();
    }
}

