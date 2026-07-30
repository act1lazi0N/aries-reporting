package com.actilazion.ariesreportingproject.service.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {
    @Mock
    ReportingTransactionRepository txRepo;
    @Mock
    MonthlySnapshotRepository snapshotRepo;
    StatementService statementService;

    private final UUID accountId = UUID.randomUUID();
    private final YearMonth currentBusinessMonth = YearMonth.of(2026, 7);

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-15T00:00:00Z"),
                ZoneOffset.UTC);
        statementService = new StatementService(txRepo, snapshotRepo, fixedClock);
    }

    @Test
    @DisplayName("getMonthlySummary: past month reads from snapshot (isFromSnapshot=true)")
    void getMonthlySummary_finalisedMonth_readsFromSnapshot() {
        YearMonth pastMonth = currentBusinessMonth.minusMonths(2);

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
        YearMonth currentMonth = currentBusinessMonth;

        when(txRepo.sumDebitByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("2000000"));
        when(txRepo.sumCreditByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.countByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(7L);

        var result = statementService.getMonthlySummary(
                accountId, currentMonth.getYear(), currentMonth.getMonthValue());

        assertThat(result.isFromSnapshot()).isFalse();
        assertThat(result.totalDebit()).isEqualByComparingTo("2000000");
        assertThat(result.totalCredit()).isEqualByComparingTo("3000000");
        assertThat(result.txCount()).isEqualTo(7);

        // Snapshot is not queried for the current month
        verifyNoInteractions(snapshotRepo);
    }

    @Test
    @DisplayName("getMonthlySummary: past month without snapshot returns empty without crashing")
    void getMonthlySummary_noSnapshot_returnsEmpty() {
        YearMonth pastMonth = currentBusinessMonth.minusMonths(3);

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

    @Test
    @DisplayName("getStatement: future-only range does not include current month totals")
    void getStatement_futureOnlyRange_doesNotIncludeCurrentMonthTotals() {
        YearMonth futureMonth = currentBusinessMonth.plusMonths(1);
        when(txRepo.findByAccountAndPeriod(eq(accountId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = statementService.getStatement(
                accountId, futureMonth, futureMonth, PageRequest.of(0, 20));

        assertThat(result.totalDebit()).isEqualByComparingTo("0");
        assertThat(result.totalCredit()).isEqualByComparingTo("0");
        assertThat(result.txCount()).isZero();
        verify(txRepo, never()).sumDebitByAccountAndPeriod(any(), any(), any());
        verify(txRepo, never()).sumCreditByAccountAndPeriod(any(), any(), any());
        verify(txRepo, never()).countByAccountAndPeriod(any(), any(), any());
        verifyNoInteractions(snapshotRepo);
    }

    @Test
    @DisplayName("getStatement: rejects inverted month ranges")
    void getStatement_fromAfterTo_throws() {
        YearMonth from = currentBusinessMonth;
        YearMonth to = from.minusMonths(1);

        assertThatThrownBy(() -> statementService.getStatement(
                accountId, from, to, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before or equal to to");
    }

    @Test
    @DisplayName("getStatement: missing past snapshot falls back to live aggregates")
    void getStatement_missingSnapshot_usesLiveAggregates() {
        YearMonth pastMonth = currentBusinessMonth.minusMonths(1);
        when(snapshotRepo.findByAccountIdAndYearAndMonth(
                eq(accountId), eq((short) pastMonth.getYear()),
                eq((short) pastMonth.getMonthValue())))
                .thenReturn(Optional.empty());
        when(txRepo.sumDebitByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("10.00"));
        when(txRepo.sumCreditByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(new BigDecimal("25.00"));
        when(txRepo.countByAccountAndPeriod(eq(accountId), any(), any()))
                .thenReturn(3L);
        when(txRepo.findByAccountAndPeriod(eq(accountId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = statementService.getStatement(
                accountId, pastMonth, pastMonth, PageRequest.of(0, 20));

        assertThat(result.totalDebit()).isEqualByComparingTo("10.00");
        assertThat(result.totalCredit()).isEqualByComparingTo("25.00");
        assertThat(result.txCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getStatement: uses Ho Chi Minh half-open month boundaries")
    void getStatement_usesBusinessZoneHalfOpenRange() {
        YearMonth month = YearMonth.of(2026, 8);
        when(txRepo.findByAccountAndPeriod(eq(accountId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        statementService.getStatement(accountId, month, month, PageRequest.of(0, 20));

        verify(txRepo).findByAccountAndPeriod(
                eq(accountId),
                eq(OffsetDateTime.parse("2026-08-01T00:00:00+07:00")),
                eq(OffsetDateTime.parse("2026-09-01T00:00:00+07:00")),
                eq(PageRequest.of(0, 20)));
    }
}
