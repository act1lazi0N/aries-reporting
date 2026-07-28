package com.actilazion.ariesreportingproject.service.job;

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
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Run at 00:00 1st every month - sent statements last month
 * Idempotency: check EmailLog before sending email - avoid sending duplicate emails
 * Rate limiting: sleep 100ms between each email - avoiding SMTP throttling
 * Error isolation: error non-stopping job, log-only and continue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyEmailJob {
    private static final int MAX_EMAIL_STATEMENT_ROWS = 500;
    private static final int ACTIVE_USER_PAGE_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StatementService statementService;
    private final EmailService emailService;
    private final EmailDeliveryClaimService emailDeliveryClaimService;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final UserViewRepository userViewRepository;
    private final AccountViewRepository accountViewRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void sendMonthlyStatements() {
        YearMonth lastMonth = YearMonth.now(clock.withZone(BUSINESS_ZONE)).minusMonths(1);
        String billingMonth = lastMonth.toString();

        log.info("[EMAIL-JOB] Starting monthly email job for billing month: {}", billingMonth);

        int sent = 0, skipped = 0, failed = 0;

        Slice<UUID> activeUsers;
        int pageNumber = 0;
        do {
            activeUsers = userViewRepository.findActiveUserIds(PageRequest.of(
                    pageNumber++, ACTIVE_USER_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            for (UUID userId : activeUsers.getContent()) {
                String idempotencyKey = EmailLog.buildIdempotencyKey(userId, billingMonth);

                EmailLog claim = emailDeliveryClaimService.claim(userId, billingMonth, idempotencyKey)
                        .orElse(null);
                if (claim == null) {
                    skipped++;
                    continue;
                }

                try {
                    boolean success = sendToUser(userId, billingMonth, lastMonth);
                    if (success) {
                        emailDeliveryClaimService.complete(claim.getId(), EmailStatus.SENT, null);
                        sent++;
                    } else {
                        emailDeliveryClaimService.complete(claim.getId(), EmailStatus.SKIPPED, "No transactions this month");
                        skipped++;
                    }

                    // Rate limiting
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.error("[EMAIL-JOB] Failed for userId={}: {}", userId, e.getMessage());
                    emailDeliveryClaimService.complete(claim.getId(), EmailStatus.FAILED, "Email send failed");
                    failed++;
                }
            }
        } while (activeUsers.hasNext());
        log.info("[EMAIL-JOB] Done. month={} sent={} skipped={} failed={}",
                billingMonth, sent, skipped, failed);
    }

    /**
     * Send email to a single user
     * Return false if no transactions this month
     */
    private boolean sendToUser(UUID userId, String billingMonth, YearMonth lastMonth) {
        // Get accounts from user
        var accounts = accountViewRepository.findAllByUserId(userId);

        if (accounts.isEmpty()) return false;

        AccountStatementResponse statement = buildConsolidatedStatement(
                accounts.stream().map(a -> a.getId()).toList(), lastMonth);

        // Skip email if no transactions
        if (statement.txCount() == 0
                && statement.totalDebit().signum() == 0
                && statement.totalCredit().signum() == 0) {
            return false;
        }

        // Get Top 5 transactions
        OffsetDateTime monthStart = lastMonth.atDay(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime monthEnd = lastMonth.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();

        List<ReportingTransaction> topTx = accounts.stream()
                .flatMap(account -> reportingTransactionRepository.findTopByAccountAndPeriod(
                        account.getId(), monthStart, monthEnd, PageRequest.of(0, 5)).stream())
                .sorted(Comparator.comparing(ReportingTransaction::getAmount).reversed())
                .limit(5)
                .toList();

        // Get email and full name
        var userOpt = userViewRepository.findById(userId);
        if (userOpt.isEmpty()) return false;

        var user = userOpt.get();

        emailService.sendMonthlyStatement(
                user.getEmail(), user.getFullName(), billingMonth, statement, topTx
        );

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
