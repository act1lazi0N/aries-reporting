package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.dto.response.FailedTransactionReportResponse;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FailedTransactionReportServiceTest {
    @Mock
    ReportingTransactionRepository txRepo;
    @InjectMocks
    FailedTransactionReportService service;

    private final OffsetDateTime from = OffsetDateTime.now().minusDays(7);
    private final OffsetDateTime to = OffsetDateTime.now();

    @Test
    @DisplayName("getFailedReport: calculate failureRate correctly")
    void getFailedReport_calculatesFailureRateCorrectly() {
        // 100 completed, 10 failed, 5 pending -> total=115, failRate=8.70%
        when(txRepo.countByStatusAndPeriod(any(), any())).thenReturn(List.of(
                new Object[]{"COMPLETED", 100L},
                new Object[]{"FAILED", 10L},
                new Object[]{"PENDING", 5L}
        ));
        when(txRepo.countFailedByHour(any(), any())).thenReturn(List.of());

        var result = service.getFailedReport(from, to);

        assertThat(result.totalTransactions()).isEqualTo(115L);
        assertThat(result.failedCount()).isEqualTo(10L);
        assertThat(result.completedCount()).isEqualTo(100L);
        assertThat(result.failureRate()).isEqualTo(8.70);
    }

    @Test
    @DisplayName("getFailedReport: failuresByHour always has 24 slots")
    void getFailedReport_always24HourlySlots() {
        when(txRepo.countByStatusAndPeriod(any(), any())).thenReturn(List.of(
                new Object[]{"COMPLETED", 50L},
                new Object[]{"FAILED", 5L}
        ));
        // Only has failures at 14 and 15
        when(txRepo.countFailedByHour(any(), any())).thenReturn(List.of(
                new Object[]{14, 3L},
                new Object[]{15, 2L}
        ));

        var result = service.getFailedReport(from, to);

        assertThat(result.failuresByHour()).hasSize(24);
        assertThat(result.failuresByHour().get(14).failedCount()).isEqualTo(3L);
        assertThat(result.failuresByHour().get(15).failedCount()).isEqualTo(2L);
        assertThat(result.failuresByHour().getFirst().failedCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getFailedReport: no transaction has failureRate = 0.0")
    void getFailedReport_noTransactions_zeroRate() {
        when(txRepo.countByStatusAndPeriod(any(), any())).thenReturn(List.of());
        when(txRepo.countFailedByHour(any(), any())).thenReturn(List.of());

        var result = service.getFailedReport(from, to);

        assertThat(result.totalTransactions()).isEqualTo(0L);
        assertThat(result.failureRate()).isEqualTo(0.0);
        assertThat(result.breakdown()).isEmpty();
    }

    @Test
    @DisplayName("getFailedReport: breakdown percentage in total = 100%")
    void getFailedReport_breakdownSumsTo100() {
        when(txRepo.countByStatusAndPeriod(any(), any())).thenReturn(List.of(
                new Object[]{"COMPLETED", 80L},
                new Object[]{"FAILED", 15L},
                new Object[]{"PENDING", 5L}
        ));
        when(txRepo.countFailedByHour(any(), any())).thenReturn(List.of());

        var result = service.getFailedReport(from, to);

        double totalPct = result.breakdown().stream()
                .mapToDouble(FailedTransactionReportResponse.StatusBreakdown::percentage)
                .sum();

        // Allowing minor error can be due to rounding
        assertThat(totalPct).isBetween(99.0, 101.0);
    }
}
