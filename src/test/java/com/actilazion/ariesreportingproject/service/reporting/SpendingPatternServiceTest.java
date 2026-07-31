package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingPatternServiceTest {
    @Mock
    ReportingTransactionRepository reportingTransactionRepository;
    @InjectMocks
    SpendingPatternService service;

    @Test
    @DisplayName("getSpendingPattern: keeps hourly volume as BigDecimal")
    void getSpendingPattern_keepsHourlyVolumeAsBigDecimal() {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-01T23:59:59Z");
        when(reportingTransactionRepository.findSpendingByHour(accountId, from, to))
                .thenReturn(List.of(
                        new Object[]{9, 2L, new BigDecimal("100.10")},
                        new Object[]{14, 1L, new BigDecimal("250.25")}));

        var result = service.getSpendingPattern(accountId, from, to);

        assertThat(result.hourlySpending()).hasSize(24);
        assertThat(result.hourlySpending().get(9).volume())
                .isEqualByComparingTo("100.10");
        assertThat(result.hourlySpending().get(14).volume())
                .isEqualByComparingTo("250.25");
        assertThat(result.peakHour()).isEqualTo(14);
    }
}
