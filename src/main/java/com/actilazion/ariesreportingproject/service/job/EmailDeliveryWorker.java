package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import com.actilazion.ariesreportingproject.service.export.EmailService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryWorker {
    private static final int MAX_EMAIL_STATEMENT_ROWS = 500;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StatementService statementService;
    private final EmailService emailService;
    private final EmailDeliveryClaimService deliveryQueue;
    private final EmailRateLimiter rateLimiter;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final UserViewRepository userViewRepository;
    private final AccountViewRepository accountViewRepository;
    private final EmailDeliveryProperties properties;
    private final Clock clock;

    public void process(EmailLog delivery) {
        int attempt = delivery.getAttemptCount();
        try {
            if (!rateLimiter.tryAcquire()) {
                deliveryQueue.retry(delivery.getId(), "SMTP rate limit", OffsetDateTime.now(clock)
                        .plus(properties.getRateLimitRetryDelay()));
                return;
            }

            attempt = deliveryQueue.incrementAttempt(delivery.getId());
            YearMonth billingMonth = YearMonth.parse(delivery.getBillingMonth());
            if (sendToUser(delivery.getUserId(), delivery.getBillingMonth(), billingMonth)) {
                deliveryQueue.complete(delivery.getId(), EmailStatus.SENT, null);
            } else {
                deliveryQueue.complete(delivery.getId(), EmailStatus.SKIPPED,
                        "No transactions this month");
            }
        } catch (Exception exception) {
            handleFailure(delivery, attempt, exception);
        }
    }

    private void handleFailure(EmailLog delivery, int attempt, Exception exception) {
        String reason = "Email delivery failed";
        log.error("[EMAIL-WORKER] Failed deliveryId={} userId={} attempt={}: {}",
                delivery.getId(), delivery.getUserId(), attempt, exception.getMessage());
        if (attempt >= properties.getMaxAttempts()) {
            deliveryQueue.complete(delivery.getId(), EmailStatus.FAILED, reason);
            return;
        }
        deliveryQueue.retry(delivery.getId(), reason, OffsetDateTime.now(clock)
                .plus(retryDelay(attempt)));
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt, 0), 6);
        Duration delay = properties.getRetryBaseDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.getRetryMaxDelay()) > 0
                ? properties.getRetryMaxDelay() : delay;
    }

    private boolean sendToUser(UUID userId, String billingMonth, YearMonth lastMonth) {
        var accounts = accountViewRepository.findAllByUserId(userId);
        if (accounts.isEmpty()) return false;

        AccountStatementResponse statement = buildConsolidatedStatement(
                accounts.stream().map(account -> account.getId()).toList(), lastMonth);
        if (statement.txCount() == 0
                && statement.totalDebit().signum() == 0
                && statement.totalCredit().signum() == 0) {
            return false;
        }

        OffsetDateTime monthStart = lastMonth.atDay(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime monthEnd = lastMonth.plusMonths(1).atDay(1)
                .atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        List<ReportingTransaction> topTransactions = accounts.stream()
                .flatMap(account -> reportingTransactionRepository.findTopByAccountAndPeriod(
                        account.getId(), monthStart, monthEnd, PageRequest.of(0, 5)).stream())
                .sorted(Comparator.comparing(ReportingTransaction::getAmount).reversed())
                .limit(5)
                .toList();

        var user = userViewRepository.findById(userId).orElse(null);
        if (user == null) return false;
        emailService.sendMonthlyStatement(user.getEmail(), user.getFullName(), billingMonth,
                statement, topTransactions);
        return true;
    }

    private AccountStatementResponse buildConsolidatedStatement(List<UUID> accountIds, YearMonth month) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int txCount = 0;
        for (UUID accountId : accountIds) {
            AccountStatementResponse accountStatement = statementService.getStatement(
                    accountId, month, month, PageRequest.of(0, MAX_EMAIL_STATEMENT_ROWS));
            totalDebit = totalDebit.add(accountStatement.totalDebit());
            totalCredit = totalCredit.add(accountStatement.totalCredit());
            txCount += accountStatement.txCount();
        }
        return AccountStatementResponse.builder()
                .accountId(accountIds.getFirst())
                .periodFrom(month.toString())
                .periodTo(month.toString())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .netFlow(totalCredit.subtract(totalDebit))
                .txCount(txCount)
                .build();
    }
}
