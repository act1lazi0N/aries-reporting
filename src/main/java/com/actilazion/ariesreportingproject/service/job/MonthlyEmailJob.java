package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyEmailJob {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EmailDeliveryClaimService deliveryQueue;
    private final UserViewRepository userViewRepository;
    private final EmailDeliveryProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void sendMonthlyStatements() {
        YearMonth billingMonth = YearMonth.now(clock.withZone(BUSINESS_ZONE)).minusMonths(1);
        int queued = 0;
        Slice<UUID> activeUsers;
        int pageNumber = 0;
        do {
            activeUsers = userViewRepository.findActiveUserIds(PageRequest.of(
                    pageNumber++, properties.getActiveUserPageSize(),
                    Sort.by(Sort.Direction.ASC, "id")));
            for (UUID userId : activeUsers.getContent()) {
                String month = billingMonth.toString();
                deliveryQueue.enqueue(userId, month, EmailLog.buildIdempotencyKey(userId, month));
                queued++;
            }
        } while (activeUsers.hasNext());

        log.info("[EMAIL-JOB] Queued monthly statements month={} users={}",
                billingMonth, queued);
    }
}
