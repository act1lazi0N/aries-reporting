package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {
    @Mock
    ReportingTransactionRepository txRepo;
    @Mock
    MonthlySnapshotRepository snapshotRepo;
    @InjectMocks
    StatementService statementService;

    private final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("getMonthlySummary: past month reads from snapshot (isFromSnapshot=true)")
    void getMonthlySummary_finalisedMonth_readsFromSnapshot() {
        YearMonth pastMonth = YearMonth.now().minusMonths(2);

        MonthlySnapshot snap = MonthlySnapshot.builder()
                .accountId(accountId)
                .year((short) pastMonth.getYear())
                .month((short) pastMonth.getMonthValue())
                .totalDebit(new BigDecimal("5000000"))
                .totalCredit(new BigDecimal("8000000"))
                .openingBalance(new BigDecimal("10000000"))
                .closingBalance(new BigDecimal("13000000"))
                .txCount(10)
                .isFinalised(true)
                .build();

        when(snapshotRepo.findByAccountIdAndYearAndMonth(
                eq(accountId),
                eq((short) pastMonth.getYear()),
                eq((short) pastMonth.getMonthValue())))
                .thenReturn(Optional.of(snap));

        var result = statementService.getMonthlySummary(
                accountId, pastMonth.getYear(), pastMonth.getMonthValue());

        assertThat(result.isFromSnapshot()).isTrue();
        assertThat(result.totalDebit()).isEqualByComparingTo("5000000");
        assertThat(result.totalCredit()).isEqualByComparingTo("8000000");

        // Does not query txRepository when a snapshot exists
        verifyNoInteractions(txRepo);
    }

    @Test
    @DisplayName("getMonthlySummary: current month queries on the fly (isFromSnapshot=false)")
    void getMonthlySummary_currentMonth_queriesOnTheFly() {
        YearMonth currentMonth = YearMonth.now();

        when(txRepo.sumDebitByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("2000000"));
        when(txRepo.sumCreditByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("3000000"));

        var result = statementService.getMonthlySummary(
                accountId, currentMonth.getYear(), currentMonth.getMonthValue());

        assertThat(result.isFromSnapshot()).isFalse();
        assertThat(result.totalDebit()).isEqualByComparingTo("2000000");
        assertThat(result.totalCredit()).isEqualByComparingTo("3000000");

        // Snapshot is not queried for the current month
        verifyNoInteractions(snapshotRepo);
    }

    @Test
    @DisplayName("getMonthlySummary: past month without snapshot returns empty without crashing")
    void getMonthlySummary_noSnapshot_returnsEmpty() {
        YearMonth pastMonth = YearMonth.now().minusMonths(3);

        when(snapshotRepo.findByAccountIdAndYearAndMonth(any(), any(), any()))
                .thenReturn(Optional.empty());

        var result = statementService.getMonthlySummary(
                accountId, pastMonth.getYear(), pastMonth.getMonthValue());

        assertThat(result.totalDebit()).isEqualByComparingTo("0");
        assertThat(result.totalCredit()).isEqualByComparingTo("0");
        assertThat(result.isFromSnapshot()).isTrue();
    }

    @Test
    @DisplayName("getTopTransactions: delegates the correct parameters to repository")
    void getTopTransactions_delegatesCorrectly() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(30);
        OffsetDateTime to   = OffsetDateTime.now();

        when(txRepo.findTopByAccountAndPeriod(
                eq(accountId), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        var result = statementService.getTopTransactions(accountId, from, to, 5);

        assertThat(result).isEmpty();
        verify(txRepo).findTopByAccountAndPeriod(
                eq(accountId), eq(from), eq(to),
                eq(PageRequest.of(0, 5)));
    }
}
