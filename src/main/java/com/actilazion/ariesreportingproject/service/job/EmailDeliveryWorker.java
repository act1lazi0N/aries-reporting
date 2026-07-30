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
            YearMonth billingMonth = YearMonth.parse(delivery.getBillingMonth());
            attempt = deliveryQueue.incrementAttempt(delivery.getId(), delivery.getClaimToken());
            DeliveryContent content = buildContent(delivery.getUserId(), billingMonth);
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!content.hasTransactions()) {
                if (withinEmptyStatementGracePeriod(delivery, now)) {
                    deliveryQueue.retry(delivery.getId(), delivery.getClaimToken(),
                            "No transactions yet", now.plus(properties.getRetryBaseDelay()));
                } else {
                    deliveryQueue.complete(delivery.getId(), delivery.getClaimToken(),
                            EmailStatus.SKIPPED, "No transactions this month");
                }
                return;
            }

            if (!rateLimiter.tryAcquire()) {
                deliveryQueue.retry(delivery.getId(), delivery.getClaimToken(), "SMTP rate limit",
                        now.plus(properties.getRateLimitRetryDelay()));
                return;
            }

            emailService.sendMonthlyStatement(content.email(), content.fullName(),
                    delivery.getBillingMonth(), content.statement(), content.topTransactions(),
                    delivery.getIdempotencyKey());
            deliveryQueue.complete(delivery.getId(), delivery.getClaimToken(), EmailStatus.SENT, null);
        } catch (Exception exception) {
            handleFailure(delivery, attempt, exception);
        }
    }

    private void handleFailure(EmailLog delivery, int attempt, Exception exception) {
        String reason = "Email delivery failed";
        log.error("[EMAIL-WORKER] Failed deliveryId={} userId={} attempt={}: {}",
                delivery.getId(), delivery.getUserId(), attempt, exception.getMessage());
        if (attempt >= properties.getMaxAttempts()) {
            deliveryQueue.complete(delivery.getId(), delivery.getClaimToken(), EmailStatus.FAILED, reason);
            return;
        }
        deliveryQueue.retry(delivery.getId(), delivery.getClaimToken(), reason, OffsetDateTime.now(clock)
                .plus(retryDelay(attempt)));
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.clamp(attempt, 0, 6);
        Duration delay = properties.getRetryBaseDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.getRetryMaxDelay()) > 0
                ? properties.getRetryMaxDelay() : delay;
    }

    private DeliveryContent buildContent(UUID userId, YearMonth lastMonth) {
        var accounts = accountViewRepository.findAllByUserId(userId);
        if (accounts.isEmpty()) return DeliveryContent.empty();

        AccountStatementResponse statement = buildConsolidatedStatement(
                accounts.stream().map(account -> account.getId()).toList(), lastMonth);
        if (statement.txCount() == 0
                && statement.totalDebit().signum() == 0
                && statement.totalCredit().signum() == 0) {
            return DeliveryContent.empty();
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
        if (user == null) return DeliveryContent.empty();
        return new DeliveryContent(user.getEmail(), user.getFullName(), statement, topTransactions, true);
    }

    private AccountStatementResponse buildConsolidatedStatement(List<UUID> accountIds, YearMonth month) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int txCount = 0;
        for (UUID accountId : accountIds) {
            AccountStatementResponse accountStatement = statementService.getLiveStatement(
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

    private boolean withinEmptyStatementGracePeriod(EmailLog delivery, OffsetDateTime now) {
        return delivery.getCreatedAt() == null
                || now.isBefore(delivery.getCreatedAt().plus(properties.getEmptyStatementGracePeriod()));
    }

    private record DeliveryContent(
            String email,
            String fullName,
            AccountStatementResponse statement,
            List<ReportingTransaction> topTransactions,
            boolean hasTransactions) {
        private static DeliveryContent empty() {
            return new DeliveryContent(null, null, null, List.of(), false);
        }
    }
}
