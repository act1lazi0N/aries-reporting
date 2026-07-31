package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.entity.transaction.AccountView;
import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import com.actilazion.ariesreportingproject.service.export.EmailService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryWorkerTest {
    @Mock StatementService statementService;
    @Mock EmailService emailService;
    @Mock EmailDeliveryClaimService deliveryQueue;
    @Mock EmailRateLimiter rateLimiter;
    @Mock ReportingTransactionRepository reportingTransactionRepository;
    @Mock UserViewRepository userViewRepository;
    @Mock AccountViewRepository accountViewRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void process_successfulStatement_marksSent() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        EmailLog delivery = delivery(userId);
        AccountView account = org.mockito.Mockito.mock(AccountView.class);
        UserView user = org.mockito.Mockito.mock(UserView.class);
        when(account.getId()).thenReturn(accountId);
        when(user.getEmail()).thenReturn("user@example.test");
        when(user.getFullName()).thenReturn("User");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken())).thenReturn(1);
        when(accountViewRepository.findAllByUserId(userId)).thenReturn(List.of(account));
        when(statementService.getLiveStatement(eq(accountId), eq(java.time.YearMonth.of(2026, 6)),
                eq(java.time.YearMonth.of(2026, 6)), eq(PageRequest.of(0, 500))))
                .thenReturn(statement(accountId));
        when(reportingTransactionRepository.findTopByAccountAndPeriod(
                eq(accountId), any(), any(), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of());
        when(userViewRepository.findById(userId)).thenReturn(Optional.of(user));

        worker().process(delivery);

        verify(emailService).sendMonthlyStatement(eq("user@example.test"), eq("User"),
                eq("2026-06"), any(), eq(List.of()), eq(delivery.getIdempotencyKey()));
        verify(deliveryQueue).complete(delivery.getId(), delivery.getClaimToken(), EmailStatus.SENT, null);
    }

    @Test
    void process_withoutPermit_requeuesWithoutSending() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        EmailLog delivery = delivery(userId);
        AccountView account = org.mockito.Mockito.mock(AccountView.class);
        UserView user = org.mockito.Mockito.mock(UserView.class);
        when(account.getId()).thenReturn(accountId);
        when(user.getEmail()).thenReturn("user@example.test");
        when(user.getFullName()).thenReturn("User");
        when(accountViewRepository.findAllByUserId(userId)).thenReturn(List.of(account));
        when(statementService.getLiveStatement(eq(accountId), any(), any(), any()))
                .thenReturn(statement(accountId));
        when(reportingTransactionRepository.findTopByAccountAndPeriod(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userViewRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken())).thenReturn(1);
        when(rateLimiter.tryAcquire()).thenReturn(false);

        worker().process(delivery);

        verify(deliveryQueue).retry(eq(delivery.getId()), eq(delivery.getClaimToken()),
                eq("SMTP rate limit"), any());
        verify(emailService, never()).sendMonthlyStatement(any(), any(), any(), any(), any(), any());
        verify(deliveryQueue).incrementAttempt(delivery.getId(), delivery.getClaimToken());
    }

    @Test
    void process_failureAfterMaxAttempts_marksFailed() {
        EmailLog delivery = delivery(UUID.randomUUID());
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setMaxAttempts(1);
        when(deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken())).thenReturn(1);
        when(accountViewRepository.findAllByUserId(delivery.getUserId()))
                .thenThrow(new IllegalStateException("source unavailable"));

        worker(properties).process(delivery);

        verify(deliveryQueue).complete(delivery.getId(), delivery.getClaimToken(), EmailStatus.FAILED,
                "Email delivery failed");
    }

    @Test
    void process_withoutTransactions_requeuesDuringGracePeriod() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        EmailLog delivery = delivery(userId);
        delivery.setCreatedAt(OffsetDateTime.parse("2026-06-30T22:00:00Z"));
        AccountView account = org.mockito.Mockito.mock(AccountView.class);
        when(account.getId()).thenReturn(accountId);
        when(accountViewRepository.findAllByUserId(userId)).thenReturn(List.of(account));
        when(statementService.getLiveStatement(eq(accountId), any(), any(), any()))
                .thenReturn(emptyStatement(accountId));
        when(deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken())).thenReturn(1);

        worker().process(delivery);

        verify(deliveryQueue).retry(eq(delivery.getId()), eq(delivery.getClaimToken()),
                eq("No transactions yet"), any());
        verify(emailService, never()).sendMonthlyStatement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_withoutTransactions_skipsAfterGracePeriod() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        EmailLog delivery = delivery(userId);
        delivery.setCreatedAt(OffsetDateTime.parse("2026-06-30T00:00:00Z"));
        AccountView account = org.mockito.Mockito.mock(AccountView.class);
        when(account.getId()).thenReturn(accountId);
        when(accountViewRepository.findAllByUserId(userId)).thenReturn(List.of(account));
        when(statementService.getLiveStatement(eq(accountId), any(), any(), any()))
                .thenReturn(emptyStatement(accountId));
        when(deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken())).thenReturn(1);

        worker().process(delivery);

        verify(deliveryQueue).complete(delivery.getId(), delivery.getClaimToken(),
                EmailStatus.SKIPPED, "No transactions this month");
        verify(emailService, never()).sendMonthlyStatement(any(), any(), any(), any(), any(), any());
    }

    private EmailDeliveryWorker worker() {
        return worker(new EmailDeliveryProperties());
    }

    private EmailDeliveryWorker worker(EmailDeliveryProperties properties) {
        return new EmailDeliveryWorker(statementService, emailService, deliveryQueue, rateLimiter,
                reportingTransactionRepository, userViewRepository, accountViewRepository,
                properties, clock);
    }

    private EmailLog delivery(UUID userId) {
        return EmailLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .billingMonth("2026-06")
                .idempotencyKey(EmailLog.buildIdempotencyKey(userId, "2026-06"))
                .claimToken(UUID.randomUUID())
                .status(EmailStatus.SENDING)
                .build();
    }

    private AccountStatementResponse statement(UUID accountId) {
        return AccountStatementResponse.builder()
                .accountId(accountId)
                .periodFrom("2026-06")
                .periodTo("2026-06")
                .totalDebit(new BigDecimal("10.00"))
                .totalCredit(new BigDecimal("20.00"))
                .netFlow(new BigDecimal("10.00"))
                .txCount(1)
                .build();
    }

    private AccountStatementResponse emptyStatement(UUID accountId) {
        return AccountStatementResponse.builder()
                .accountId(accountId)
                .periodFrom("2026-06")
                .periodTo("2026-06")
                .totalDebit(BigDecimal.ZERO)
                .totalCredit(BigDecimal.ZERO)
                .netFlow(BigDecimal.ZERO)
                .txCount(0)
                .build();
    }
}
