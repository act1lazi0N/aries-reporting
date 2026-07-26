package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.EmailLogRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import com.actilazion.ariesreportingproject.service.export.EmailService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StatementService statementService;
    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final UserViewRepository userViewRepository;
    private final AccountViewRepository accountViewRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void sendMonthlyStatements() {
        YearMonth lastMonth = YearMonth.now(clock.withZone(BUSINESS_ZONE)).minusMonths(1);
        String billingMonth = lastMonth.toString();

        log.info("[EMAIL-JOB] Starting monthly email job for billing month: {}", billingMonth);

        // Retrieve all users with accounts in the system
        List<UUID> addUserIds = userViewRepository.findAll()
                .stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .map(u -> u.getId())
                .toList();
        int sent = 0, skipped = 0, failed = 0;

        for (UUID userId : addUserIds) {
            String idempotencyKey = EmailLog.buildIdempotencyKey(userId, billingMonth);

            EmailLog existingLog = emailLogRepository.findByIdempotencyKey(idempotencyKey)
                    .orElse(null);
            if (existingLog != null && existingLog.getStatus() == EmailStatus.SENT) {
                skipped++;
                continue;
            }

            try {
                boolean success = sendToUser(userId, billingMonth, lastMonth);
                if (success) {
                    saveEmailLog(existingLog, userId, billingMonth, idempotencyKey, EmailStatus.SENT, null);
                    sent++;
                } else {
                    saveEmailLog(existingLog, userId, billingMonth, idempotencyKey, EmailStatus.SKIPPED, "No transactions this month");
                    skipped++;
                }

                // Rate limiting
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("[EMAIL-JOB] Failed for userId={}: {}",
                        userId, e.getMessage());
                saveEmailLog(existingLog, userId, billingMonth,
                        idempotencyKey, EmailStatus.FAILED, e.getMessage());
                failed++;
            }
        }
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

        // Use the first account to generate statement
        // Production: possible to send an email summarizing multiple accounts
        UUID accountId = accounts.get(0).getId();
        AccountStatementResponse statement = statementService.getStatement(
                accountId, lastMonth, lastMonth,
                PageRequest.of(0, MAX_EMAIL_STATEMENT_ROWS));

        // Skip email if no transactions
        if (statement.txCount() == 0
                && statement.totalDebit().signum() == 0
                && statement.totalCredit().signum() == 0) {
            return false;
        }

        // Get Top 5 transactions
        OffsetDateTime monthStart = lastMonth.atDay(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime monthEnd = lastMonth.atEndOfMonth()
                .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        List<ReportingTransaction> topTx = reportingTransactionRepository.findTopByAccountAndPeriod(
                accountId, monthStart, monthEnd, PageRequest.of(0, 5)
        );

        // Get email and full name
        var userOpt = userViewRepository.findById(userId);
        if (userOpt.isEmpty()) return false;

        var user = userOpt.get();

        emailService.sendMonthlyStatement(
                user.getEmail(), user.getFullName(), billingMonth, statement, topTx
        );

        return true;
    }

    private void saveEmailLog(EmailLog existingLog, UUID userId, String billingMonth, String idempotencyKey, EmailStatus status, String errorReason) {
        EmailLog log = existingLog != null ? existingLog : EmailLog.builder()
                .userId(userId)
                .billingMonth(billingMonth)
                .idempotencyKey(idempotencyKey)
                .build();
        log.setStatus(status);
        log.setErrorMessage(errorReason);
        emailLogRepository.save(log);
    }
}
