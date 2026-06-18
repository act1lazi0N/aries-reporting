package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.entity.reporting.DailySnapshot;
import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.DailySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final DailySnapshotRepository dailySnapshotRepository;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final MonthlySnapshotRepository monthlySnapshotRepository;

    /**
     * Daily snapshot job - runs at 00:05 every day.
     * Snapshot the previous day for all accounts containing transactions.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(transactionManager = "reportingTransactionManager")
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[SNAPSHOT] Running daily snapshot for {}", yesterday);

        OffsetDateTime dayStart = yesterday.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime dayEnd = yesterday.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        // Fetch account IDs that have transactions on the previous day
        List<UUID> accountIds = reportingTransactionRepository.findSyncedTxIdsByPeriod(dayStart, dayEnd).stream().distinct().toList();

        int created = 0;
        for (UUID accountId : accountIds) {
            if (dailySnapshotRepository.existsByAccountIdAndSnapshotDate(accountId, yesterday)) {
                continue;
            }

            BigDecimal debit = reportingTransactionRepository.sumDebitByAccountAndPeriod(accountId, dayStart, dayEnd);
            BigDecimal credit = reportingTransactionRepository.sumCreditByAccountAndPeriod(accountId, dayStart, dayEnd);

            // Opening balance is equivalent to closing balance on the previous day
            BigDecimal opening = dailySnapshotRepository.findByAccountIdAndSnapshotDate(accountId, yesterday.minusDays(1)).map(DailySnapshot::getClosingBalance).orElse(BigDecimal.ZERO);
            BigDecimal closing = opening.add(credit).subtract(debit);

            dailySnapshotRepository.save(DailySnapshot.builder().accountId(accountId).snapshotDate(yesterday).openingBalance(opening).closingBalance(closing).totalDebit(debit).totalCredit(credit).txCount(0).build());
            created++;
        }
        log.info("[SNAPSHOT] Daily snapshot done. date={} accounts={}", yesterday, created);
    }

    /**
     * Monthly snapshot job - runs at 00:30 on the 1st every month.
     * Finalizes the previous month.
     */
    @Scheduled(cron = "0 30 0 1 * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(transactionManager = "reportingTransactionManager")
    public void runMonthlySnapshot() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("[SNAPSHOT] Running monthly snapshot for {}", lastMonth);

        OffsetDateTime monthStart = lastMonth.atDay(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime monthEnd   = lastMonth.atEndOfMonth()
                .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        List<UUID> accountIds = monthlySnapshotRepository.findAccountsWithoutSnapshotForMonth(monthStart, (short) lastMonth.getYear(), (short) lastMonth.getMonthValue());

        int finalised = 0;
        for (UUID accountId : accountIds) {
            BigDecimal debit  = reportingTransactionRepository.sumDebitByAccountAndPeriod(
                    accountId, monthStart, monthEnd);
            BigDecimal credit = reportingTransactionRepository.sumCreditByAccountAndPeriod(
                    accountId, monthStart, monthEnd);

            // Opening is equivalent to closing balance of the previous month.
            YearMonth prevMonth = lastMonth.minusMonths(1);
            BigDecimal opening  = monthlySnapshotRepository
                    .findByAccountIdAndYearAndMonth(
                            accountId,
                            (short) prevMonth.getYear(),
                            (short) prevMonth.getMonthValue())
                    .map(MonthlySnapshot::getClosingBalance)
                    .orElse(BigDecimal.ZERO);

            BigDecimal closing = opening.add(credit).subtract(debit);

            monthlySnapshotRepository.save(MonthlySnapshot.builder()
                    .accountId(accountId)
                    .year((short) lastMonth.getYear())
                    .month((short) lastMonth.getMonthValue())
                    .openingBalance(opening)
                    .closingBalance(closing)
                    .totalDebit(debit)
                    .totalCredit(credit)
                    .isFinalised(true)
                    .build());
            finalised++;
        }
        log.info("[SNAPSHOT] Monthly snapshot done. month={} accounts={}",
                lastMonth, finalised);
    }
}
