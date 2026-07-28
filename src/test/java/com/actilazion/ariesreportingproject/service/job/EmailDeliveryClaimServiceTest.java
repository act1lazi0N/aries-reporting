package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryClaimServiceTest {
    @Mock
    EmailLogRepository emailLogRepository;

    @Test
    void claimDue_marksPendingRowsSendingWithLease() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setLeaseDuration(java.time.Duration.ofMinutes(5));
        EmailLog delivery = EmailLog.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .billingMonth("2026-06")
                .idempotencyKey("key")
                .status(EmailStatus.PENDING)
                .build();
        OffsetDateTime now = OffsetDateTime.now(clock);
        when(emailLogRepository.findDueForUpdate(eq(now), eq(20)))
                .thenReturn(List.of(delivery));

        EmailDeliveryClaimService service = new EmailDeliveryClaimService(
                emailLogRepository, properties, clock);

        List<EmailLog> claimed = service.claimDue(now);

        assertThat(claimed).containsExactly(delivery);
        assertThat(delivery.getStatus()).isEqualTo(EmailStatus.SENDING);
        assertThat(delivery.getLeaseUntil()).isEqualTo(now.plusMinutes(5));
        verify(emailLogRepository).flush();
    }

    @Test
    void enqueue_usesDatabaseUniquenessForIdempotency() {
        EmailDeliveryClaimService service = new EmailDeliveryClaimService(
                emailLogRepository, new EmailDeliveryProperties(), Clock.systemUTC());
        UUID userId = UUID.randomUUID();

        service.enqueue(userId, "2026-06", userId + "::2026-06");

        verify(emailLogRepository).insertPending(userId, "2026-06", userId + "::2026-06");
    }
}
