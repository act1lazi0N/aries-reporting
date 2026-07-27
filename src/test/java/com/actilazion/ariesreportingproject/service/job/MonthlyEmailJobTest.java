package com.actilazion.ariesreportingproject.service.job;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyEmailJobTest {
    @Mock
    StatementService statementService;
    @Mock
    EmailService emailService;
    @Mock
    EmailDeliveryClaimService emailDeliveryClaimService;
    @Mock
    ReportingTransactionRepository reportingTransactionRepository;
    @Mock
    UserViewRepository userViewRepository;
    @Mock
    AccountViewRepository accountViewRepository;

    @Test
    @DisplayName("sendMonthlyStatements: consolidates all user accounts")
    void sendMonthlyStatements_consolidatesAllUserAccounts() {
        UUID userId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        YearMonth billingMonth = YearMonth.of(2026, 6);

        UserView user = mock(UserView.class);
        when(user.getId()).thenReturn(userId);
        when(user.getIsActive()).thenReturn(true);
        when(user.getEmail()).thenReturn("user@example.test");
        when(user.getFullName()).thenReturn("User One");

        AccountView firstAccount = mock(AccountView.class);
        when(firstAccount.getId()).thenReturn(firstAccountId);
        AccountView secondAccount = mock(AccountView.class);
        when(secondAccount.getId()).thenReturn(secondAccountId);

        EmailLog claim = EmailLog.builder()
                .id(claimId)
                .userId(userId)
                .billingMonth(billingMonth.toString())
                .idempotencyKey(EmailLog.buildIdempotencyKey(userId, billingMonth.toString()))
                .status(EmailStatus.SENDING)
                .build();

        when(userViewRepository.findAll()).thenReturn(List.of(user));
        when(emailDeliveryClaimService.claim(
                eq(userId),
                eq(billingMonth.toString()),
                eq(EmailLog.buildIdempotencyKey(userId, billingMonth.toString()))))
                .thenReturn(Optional.of(claim));
        when(accountViewRepository.findAllByUserId(userId))
                .thenReturn(List.of(firstAccount, secondAccount));
        when(statementService.getStatement(
                eq(firstAccountId), eq(billingMonth), eq(billingMonth), eq(PageRequest.of(0, 500))))
                .thenReturn(statement(firstAccountId, "10.00", "30.00", 2));
        when(statementService.getStatement(
                eq(secondAccountId), eq(billingMonth), eq(billingMonth), eq(PageRequest.of(0, 500))))
                .thenReturn(statement(secondAccountId, "5.00", "1.00", 1));
        when(userViewRepository.findById(userId)).thenReturn(Optional.of(user));

        MonthlyEmailJob job = new MonthlyEmailJob(
                statementService,
                emailService,
                emailDeliveryClaimService,
                reportingTransactionRepository,
                userViewRepository,
                accountViewRepository,
                Clock.fixed(Instant.parse("2026-07-01T01:00:00Z"), ZoneOffset.UTC));

        job.sendMonthlyStatements();

        ArgumentCaptor<AccountStatementResponse> statementCaptor =
                ArgumentCaptor.forClass(AccountStatementResponse.class);
        verify(emailService).sendMonthlyStatement(
                eq("user@example.test"),
                eq("User One"),
                eq("2026-06"),
                statementCaptor.capture(),
                eq(List.of()));
        AccountStatementResponse consolidated = statementCaptor.getValue();
        assertThat(consolidated.totalDebit()).isEqualByComparingTo("15.00");
        assertThat(consolidated.totalCredit()).isEqualByComparingTo("31.00");
        assertThat(consolidated.netFlow()).isEqualByComparingTo("16.00");
        assertThat(consolidated.txCount()).isEqualTo(3);
        verify(emailDeliveryClaimService).complete(claimId, EmailStatus.SENT, null);
    }

    private AccountStatementResponse statement(UUID accountId, String debit, String credit, int count) {
        BigDecimal totalDebit = new BigDecimal(debit);
        BigDecimal totalCredit = new BigDecimal(credit);
        return AccountStatementResponse.builder()
                .accountId(accountId)
                .periodFrom("2026-06")
                .periodTo("2026-06")
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .netFlow(totalCredit.subtract(totalDebit))
                .txCount(count)
                .build();
    }
}
