package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {
    @Mock
    ReportingTransactionRepository reportingTransactionRepository;
    @Mock
    RedisTemplate<String, Object> redisTemplate;
    @Mock
    ValueOperations<String, Object> valueOperations;
    @Mock
    AppProperties appProperties;

    @Test
    @DisplayName("getOverview: cache key includes full timestamp window")
    void getOverview_cacheKeyUsesFullTimestampWindow() {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T03:00:00+07:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-01T15:00:00+07:00");
        String expectedKey = "admin:overview:" + from.toInstant() + ":" + to.toInstant();
        AdminReportService service = new AdminReportService(
                reportingTransactionRepository, redisTemplate, appProperties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);
        when(reportingTransactionRepository.findDailyVolumeByPeriod(from, to))
                .thenReturn(List.of());
        when(reportingTransactionRepository.countByStatusAndPeriod(from, to))
                .thenReturn(List.of());
        when(appProperties.getCacheTtlMinutes()).thenReturn(10);

        service.getOverview(from, to);

        verify(valueOperations).get(expectedKey);
        verify(valueOperations).set(eq(expectedKey), any(), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("getOverview: keeps money volume as BigDecimal")
    void getOverview_keepsVolumeAsBigDecimal() {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-02T00:00:00Z");
        String expectedKey = "admin:overview:" + from.toInstant() + ":" + to.toInstant();
        AdminReportService service = new AdminReportService(
                reportingTransactionRepository, redisTemplate, appProperties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);
        when(reportingTransactionRepository.findDailyVolumeByPeriod(from, to))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"2026-07-01", 2L, new BigDecimal("100.10")},
                        new Object[]{"2026-07-02", 3L, new BigDecimal("200.20")}));
        when(reportingTransactionRepository.countByStatusAndPeriod(from, to))
                .thenReturn(List.<Object[]>of(new Object[]{"COMPLETED", 5L}));
        when(appProperties.getCacheTtlMinutes()).thenReturn(10);

        var result = service.getOverview(from, to);

        assertThat(result.totalVolume()).isEqualByComparingTo("300.30");
        assertThat(result.dailyVolumes().getFirst().volume())
                .isEqualByComparingTo("100.10");
    }

    @Test
    @DisplayName("getOverview: total transactions and failure rate use all statuses")
    void getOverview_failureRateUsesAllStatusCounts() {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-02T00:00:00Z");
        String expectedKey = "admin:overview:" + from.toInstant() + ":" + to.toInstant();
        AdminReportService service = new AdminReportService(
                reportingTransactionRepository, redisTemplate, appProperties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);
        when(reportingTransactionRepository.findDailyVolumeByPeriod(from, to))
                .thenReturn(List.<Object[]>of(new Object[]{"2026-07-01", 8L, new BigDecimal("800.00")}));
        when(reportingTransactionRepository.countByStatusAndPeriod(from, to))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"COMPLETED", 8L},
                        new Object[]{"FAILED", 2L},
                        new Object[]{"PENDING", 5L}));
        when(appProperties.getCacheTtlMinutes()).thenReturn(10);

        var result = service.getOverview(from, to);

        assertThat(result.totalTransactions()).isEqualTo(15);
        assertThat(result.failureRate()).isEqualTo(13.33);
    }
}
