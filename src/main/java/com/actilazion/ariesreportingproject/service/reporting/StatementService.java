package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.dto.response.TransactionSummaryResponse;
import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hybrid strategy:
 * - Finalized past months (is_finalised=true): read from monthly_snapshots (O(1)).
 * - Current, non-finalized month (is_finalised=false): query reporting_transactions on the fly.
 * <p>
 * Why not always query on the fly?
 * With large datasets (millions of transactions), querying the full history can become slow.
 * Snapshots are precomputed once and can be read back as a single row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {
    private final ReportingTransactionRepository transactionRepository;
    private final MonthlySnapshotRepository snapshotRepository;

    /**
     * Returns an account statement for the requested period.
     * Automatically selects the read strategy based on the current month.
     */
    @Transactional(transactionManager = "reportingTransactionManager", readOnly = true)
    public AccountStatementResponse getStatement(
            UUID accountId,
            YearMonth from,
            YearMonth to,
            Pageable pageable) {
        validatePeriod(from, to);

        YearMonth currentMonth = YearMonth.now();
        boolean includesCurrentMonth = !from.isAfter(currentMonth)
                && !to.isBefore(currentMonth);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int transactionCount = 0;

        // Past months: read from snapshots.
        if (from.isBefore(currentMonth)) {
            YearMonth snapshotTo = includesCurrentMonth ? currentMonth.minusMonths(1) : to;

            YearMonth cursor = from;
            while (!cursor.isAfter(snapshotTo)) {
                Optional<MonthlySnapshot> snap = snapshotRepository
                        .findByAccountIdAndYearAndMonth(
                                accountId,
                                (short) cursor.getYear(),
                                (short) cursor.getMonthValue());
                if (snap.isPresent()) {
                    totalDebit = totalDebit.add(snap.get().getTotalDebit());
                    totalCredit = totalCredit.add(snap.get().getTotalCredit());
                    transactionCount += snap.get().getTxCount();
                }
                cursor = cursor.plusMonths(1);
            }
        }

        // Current month: query on the fly.
        OffsetDateTime periodStart = from.atDay(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime periodEnd = to.atEndOfMonth()
                .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        if (includesCurrentMonth) {
            OffsetDateTime currentMonthStart = currentMonth.atDay(1)
                    .atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime now = OffsetDateTime.now();

            BigDecimal currentDebit = transactionRepository.sumDebitByAccountAndPeriod(
                    accountId, currentMonthStart, now);
            BigDecimal currentCredit = transactionRepository.sumCreditByAccountAndPeriod(
                    accountId, currentMonthStart, now);
            long currentCount = transactionRepository.countByAccountAndPeriod(
                    accountId, currentMonthStart, now);

            totalDebit = totalDebit.add(currentDebit);
            totalCredit = totalCredit.add(currentCredit);
            transactionCount += Math.toIntExact(currentCount);
        }

        // Paginated transactions are always queried on the fly.
        Page<ReportingTransaction> transactions = transactionRepository
                .findByAccountAndPeriod(accountId, periodStart, periodEnd, pageable);

        return AccountStatementResponse.builder()
                .accountId(accountId)
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .netFlow(totalCredit.subtract(totalDebit))
                .txCount(transactionCount)
                .transactions(transactions.map(this::toSummary))
                .build();
    }

    /**
     * Returns a monthly transaction summary for monthly charts.
     */
    @Transactional(transactionManager = "reportingTransactionManager", readOnly = true)
    public TransactionSummaryResponse getMonthlySummary(
            UUID accountId, int year, int month
    ) {
        YearMonth ym = YearMonth.of(year, month);
        YearMonth currentMonth = YearMonth.now();

        // Past months use snapshots.
        if (ym.isBefore(currentMonth)) {
            return snapshotRepository
                    .findByAccountIdAndYearAndMonth(
                            accountId, (short) year, (short) month)
                    .map(snap -> TransactionSummaryResponse.builder()
                            .accountId(accountId)
                            .year(year)
                            .month(month)
                            .totalDebit(snap.getTotalDebit())
                            .totalCredit(snap.getTotalCredit())
                            .txCount(snap.getTxCount())
                            .openingBalance(snap.getOpeningBalance())
                            .closingBalance(snap.getClosingBalance())
                            .isFromSnapshot(true)
                            .build())
                    .orElse(emptyMonthlySummary(accountId, year, month));
        }

        OffsetDateTime start = ym.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.now();

        BigDecimal debit = transactionRepository.sumDebitByAccountAndPeriod(
                accountId, start, end);
        BigDecimal credit = transactionRepository.sumCreditByAccountAndPeriod(
                accountId, start, end);
        long txCount = transactionRepository.countByAccountAndPeriod(
                accountId, start, end);

        return TransactionSummaryResponse.builder()
                .accountId(accountId)
                .year(year)
                .month(month)
                .totalDebit(debit)
                .totalCredit(credit)
                .txCount(Math.toIntExact(txCount))
                .isFromSnapshot(false)
                .build();
    }

    /**
     * Returns the top N the largest transactions in the requested period.
     */
    @Transactional(transactionManager = "reportingTransactionManager", readOnly = true)
    public List<ReportingTransaction> getTopTransactions(
            UUID accountId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit) {

        return transactionRepository.findTopByAccountAndPeriod(
                accountId, from, to, PageRequest.of(0, limit));
    }

    // Private helper.
    private AccountStatementResponse.TransactionLine toSummary(
            ReportingTransaction tx) {
        return new AccountStatementResponse.TransactionLine(
                tx.getId(),
                tx.getOriginalTxId(),
                tx.getFromAccountNumber(),
                tx.getToAccountNumber(),
                tx.getFromOwnerName(),
                tx.getToOwnerName(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }

    private TransactionSummaryResponse emptyMonthlySummary(
            UUID accountId, int year, int month) {
        return TransactionSummaryResponse.builder()
                .accountId(accountId).year(year).month(month)
                .totalDebit(BigDecimal.ZERO).totalCredit(BigDecimal.ZERO)
                .txCount(0).isFromSnapshot(true)
                .build();
    }

    private void validatePeriod(YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }

}
