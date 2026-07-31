package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryDispatcher {
    private final EmailDeliveryClaimService deliveryQueue;
    private final EmailDeliveryWorker worker;
    private final EmailDeliveryProperties properties;
    @Qualifier("emailTaskExecutor")
    private final ThreadPoolTaskExecutor emailTaskExecutor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.reporting.email.dispatch-delay-ms:1000}")
    public void dispatchDueDeliveries() {
        int availableWorkers = Math.max(0,
                emailTaskExecutor.getMaxPoolSize() - emailTaskExecutor.getActiveCount());
        int claimLimit = Math.min(properties.getDispatchBatchSize(), availableWorkers);
        if (claimLimit == 0) {
            return;
        }

        for (EmailLog delivery : deliveryQueue.claimDue(OffsetDateTime.now(clock), claimLimit)) {
            try {
                emailTaskExecutor.execute(() -> worker.process(delivery));
            } catch (RuntimeException rejected) {
                deliveryQueue.retry(delivery.getId(), delivery.getClaimToken(), "Email executor saturated",
                        OffsetDateTime.now(clock).plus(properties.getRateLimitRetryDelay()));
                log.warn("[EMAIL-DISPATCHER] Executor rejected deliveryId={}", delivery.getId());
            }
        }
    }
}
