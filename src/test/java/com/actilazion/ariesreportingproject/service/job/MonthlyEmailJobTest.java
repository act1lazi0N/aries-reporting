package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyEmailJobTest {
    @Mock
    EmailDeliveryClaimService deliveryQueue;
    @Mock
    UserViewRepository userViewRepository;

    @Test
    void sendMonthlyStatements_enqueuesEachActiveUserAndContinuesPaging() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        PageRequest firstPage = PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id"));
        PageRequest secondPage = PageRequest.of(1, 100, Sort.by(Sort.Direction.ASC, "id"));
        when(userViewRepository.findActiveUserIds(firstPage))
                .thenReturn(new SliceImpl<>(List.of(firstUserId), firstPage, true));
        when(userViewRepository.findActiveUserIds(secondPage))
                .thenReturn(new SliceImpl<>(List.of(secondUserId), secondPage, false));

        MonthlyEmailJob job = new MonthlyEmailJob(
                deliveryQueue,
                userViewRepository,
                new EmailDeliveryProperties(),
                Clock.fixed(Instant.parse("2026-07-01T01:00:00Z"), ZoneOffset.UTC));

        job.sendMonthlyStatements();

        verify(deliveryQueue).enqueue(eq(firstUserId), eq("2026-06"),
                eq(firstUserId + "::2026-06"));
        verify(deliveryQueue).enqueue(eq(secondUserId), eq("2026-06"),
                eq(secondUserId + "::2026-06"));
    }
}
