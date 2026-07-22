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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
