package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.response.AdminOverviewResponse;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only aggregates. These are expensive queries cached in Redis with a 10-minute TTL.
 * <p>
 * Cache key pattern: "admin:overview:{from}:{to}"
 * Invalidation: TTL-based, so manual invalidation is not required.
 * Trade-off: dashboard data can be stale by up to 10 minutes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportService {
    private static final String CACHE_PREFIX = "admin:overview:";
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    @Transactional(transactionManager = "reportingTransactionManager", readOnly = true)
    @SuppressWarnings("unchecked")
    public AdminOverviewResponse getOverview(
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String cacheKey = CACHE_PREFIX + from.toLocalDate() + ":" + to.toLocalDate();

        // Check cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof AdminOverviewResponse response) {
            log.debug("[ADMIN] Cache hit for key={}", cacheKey);
            return response;
        }

        log.debug("[ADMIN] Cache miss - computing overview from={} to={}", from, to);

        // Query aggregate
        List<Object[]> dailyVolume  = reportingTransactionRepository.findDailyVolumeByPeriod(from, to);
        List<Object[]> statusCounts = reportingTransactionRepository.countByStatusAndPeriod(from, to);

        AdminOverviewResponse overview = buildOverview(dailyVolume, statusCounts, from, to);

        // Cache with TTL
        Duration ttl = Duration.ofMinutes(appProperties.getCacheTtlMinutes());
        redisTemplate.opsForValue().set(cacheKey, overview, ttl);

        return overview;
    }

    private AdminOverviewResponse buildOverview(
            List<Object[]> dailyVolume,
            List<Object[]> statusCounts,
            OffsetDateTime from,
            OffsetDateTime to)
    {
        List<AdminOverviewResponse.DailyVolume> volumes = new ArrayList<>();
        long totalTransactions = 0;
        double totalVolume = 0;

        for (Object[] row : dailyVolume) {
            String day = row[0].toString();
            long count = ((Number) row[1]).longValue();
            double volume =  ((Number) row[2]).doubleValue();
            volumes.add(new AdminOverviewResponse.DailyVolume(day, count, volume));
            totalTransactions += count;
            totalVolume += volume;
        }

        long completed = 0, failed = 0, pending = 0;
        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case "COMPLETED" -> completed = count;
                case "FAILED" -> failed = count;
                case "PENDING" -> pending = count;
            }
        }

        double failRate = totalTransactions > 0 ? (double) failed / totalTransactions * 100 : 0;

        return AdminOverviewResponse.builder()
                .periodFrom(from.toLocalDate().toString())
                .periodTo(to.toLocalDate().toString())
                .totalTransactions(totalTransactions)
                .totalVolume(totalVolume)
                .completedCount(completed)
                .failedCount(failed)
                .pendingCount(pending)
                .failureRate(Math.round(failRate * 100.0) / 100.0)
                .dailyVolumes(volumes)
                .build();
    }
}
