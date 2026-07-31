package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryDispatcherTest {
    @Mock
    EmailDeliveryClaimService deliveryQueue;
    @Mock
    EmailDeliveryWorker worker;
    @Mock
    EmailDeliveryProperties properties;
    @Mock
    ThreadPoolTaskExecutor emailTaskExecutor;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void dispatchDueDeliveries_claimsOnlyAvailableWorkerSlots() {
        EmailLog delivery = delivery();
        when(properties.getDispatchBatchSize()).thenReturn(20);
        when(emailTaskExecutor.getMaxPoolSize()).thenReturn(2);
        when(emailTaskExecutor.getActiveCount()).thenReturn(1);
        when(deliveryQueue.claimDue(any(), eq(1))).thenReturn(List.of(delivery));

        dispatcher().dispatchDueDeliveries();

        verify(deliveryQueue).claimDue(any(), eq(1));
        verify(emailTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void dispatchDueDeliveries_doesNotClaimWhenWorkersAreBusy() {
        when(properties.getDispatchBatchSize()).thenReturn(20);
        when(emailTaskExecutor.getMaxPoolSize()).thenReturn(2);
        when(emailTaskExecutor.getActiveCount()).thenReturn(2);

        dispatcher().dispatchDueDeliveries();

        verify(deliveryQueue, never()).claimDue(any(), any(Integer.class));
        verify(emailTaskExecutor, never()).execute(any(Runnable.class));
    }

    private EmailDeliveryDispatcher dispatcher() {
        return new EmailDeliveryDispatcher(
                deliveryQueue, worker, properties, emailTaskExecutor, clock);
    }

    private EmailLog delivery() {
        UUID userId = UUID.randomUUID();
        return EmailLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .billingMonth("2026-06")
                .idempotencyKey(EmailLog.buildIdempotencyKey(userId, "2026-06"))
                .claimToken(UUID.randomUUID())
                .build();
    }
}
