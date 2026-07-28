package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.entity.reporting.DailySnapshot;
import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.DailySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DailySnapshotRepository dailySnapshotRepository;
    private final ReportingTransactionRepository reportingTransactionRepository;
    private final MonthlySnapshotRepository monthlySnapshotRepository;
    private final Clock clock;

    /**
     * Daily snapshot job - runs at 00:05 every day.
     * Snapshot the previous day for all accounts containing transactions.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(transactionManager = "reportingTransactionManager")
    public void runDailySnapshot() {
        LocalDate yesterday = LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusDays(1);
        log.info("[SNAPSHOT] Running daily snapshot for {}", yesterday);

        OffsetDateTime dayStart = startOfDay(yesterday);
        OffsetDateTime dayEnd = startOfDay(yesterday.plusDays(1)).minusNanos(1);

        Set<UUID> accountIds = accountIdsForPeriod(dayStart, dayEnd);

        int created = 0;
        for (UUID accountId : accountIds) {
            BigDecimal debit = reportingTransactionRepository.sumDebitByAccountAndPeriod(accountId, dayStart, dayEnd);
            BigDecimal credit = reportingTransactionRepository.sumCreditByAccountAndPeriod(accountId, dayStart, dayEnd);
            long txCount = reportingTransactionRepository.countByAccountAndPeriod(accountId, dayStart, dayEnd);

            BigDecimal opening = dailySnapshotRepository
                    .findFirstByAccountIdAndSnapshotDateBeforeOrderBySnapshotDateDesc(accountId, yesterday)
                    .map(DailySnapshot::getClosingBalance)
                    .orElse(BigDecimal.ZERO);
            BigDecimal closing = opening.add(credit).subtract(debit);

            DailySnapshot snapshot = dailySnapshotRepository
                    .findByAccountIdAndSnapshotDate(accountId, yesterday)
                    .orElseGet(() -> DailySnapshot.builder()
                            .accountId(accountId)
                            .snapshotDate(yesterday)
                            .build());
            snapshot.setOpeningBalance(opening);
            snapshot.setClosingBalance(closing);
            snapshot.setTotalDebit(debit);
            snapshot.setTotalCredit(credit);
            snapshot.setTxCount(Math.toIntExact(txCount));
            dailySnapshotRepository.save(snapshot);
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
        YearMonth lastMonth = YearMonth.now(clock.withZone(BUSINESS_ZONE)).minusMonths(1);
        log.info("[SNAPSHOT] Running monthly snapshot for {}", lastMonth);

        OffsetDateTime monthStart = startOfDay(lastMonth.atDay(1));
        OffsetDateTime monthEnd = startOfDay(lastMonth.plusMonths(1).atDay(1)).minusNanos(1);

        Set<UUID> accountIds = accountIdsForPeriod(monthStart, monthEnd);

        int finalised = 0;
        for (UUID accountId : accountIds) {
            BigDecimal debit  = reportingTransactionRepository.sumDebitByAccountAndPeriod(
                    accountId, monthStart, monthEnd);
            BigDecimal credit = reportingTransactionRepository.sumCreditByAccountAndPeriod(
                    accountId, monthStart, monthEnd);
            long txCount = reportingTransactionRepository.countByAccountAndPeriod(
                    accountId, monthStart, monthEnd);

            BigDecimal opening  = monthlySnapshotRepository
                    .findLatestBeforeMonth(
                            accountId,
                            (short) lastMonth.getYear(),
                            (short) lastMonth.getMonthValue(),
                            PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .map(MonthlySnapshot::getClosingBalance)
                    .orElse(BigDecimal.ZERO);

            BigDecimal closing = opening.add(credit).subtract(debit);

            MonthlySnapshot snapshot = monthlySnapshotRepository
                    .findByAccountIdAndYearAndMonth(
                            accountId,
                            (short) lastMonth.getYear(),
                            (short) lastMonth.getMonthValue())
                    .orElseGet(() -> MonthlySnapshot.builder()
                            .accountId(accountId)
                            .year((short) lastMonth.getYear())
                            .month((short) lastMonth.getMonthValue())
                            .build());
            snapshot.setOpeningBalance(opening);
            snapshot.setClosingBalance(closing);
            snapshot.setTotalDebit(debit);
            snapshot.setTotalCredit(credit);
            snapshot.setTxCount(Math.toIntExact(txCount));
            snapshot.setIsFinalised(true);
            monthlySnapshotRepository.save(snapshot);
            finalised++;
        }
        log.info("[SNAPSHOT] Monthly snapshot done. month={} accounts={}",
                lastMonth, finalised);
    }

    private Set<UUID> accountIdsForPeriod(OffsetDateTime from, OffsetDateTime to) {
        Set<UUID> accountIds = new LinkedHashSet<>();
        accountIds.addAll(reportingTransactionRepository.findDistinctFromAccountIdsByPeriod(from, to));
        accountIds.addAll(reportingTransactionRepository.findDistinctToAccountIdsByPeriod(from, to));
        return accountIds;
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }
}
