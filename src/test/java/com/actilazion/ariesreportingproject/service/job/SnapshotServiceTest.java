package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.entity.reporting.DailySnapshot;
import com.actilazion.ariesreportingproject.entity.reporting.MonthlySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.DailySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {
    @Mock
    ReportingTransactionRepository txRepo;
    @Mock
    DailySnapshotRepository dailyRepo;
    @Mock
    MonthlySnapshotRepository monthlyRepo;
    SnapshotService snapshotService;

    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T17:30:00Z"), ZoneOffset.UTC);
        snapshotService = new SnapshotService(dailyRepo, txRepo, monthlyRepo, clock);
    }

    @Test
    @DisplayName("runDailySnapshot: creates snapshot with the correct balance")
    void runDailySnapshot_createsCorrectSnapshot() {
        LocalDate yesterday = LocalDate.of(2026, 6, 30);

        // Account had transactions yesterday
        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        when(dailyRepo.existsByAccountIdAndSnapshotDate(any(), eq(yesterday)))
                .thenReturn(false);

        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("5000000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(4L);

        // Opening = 10,000,000 from the day before yesterday
        when(dailyRepo.findByAccountIdAndSnapshotDate(any(), eq(yesterday.minusDays(1))))
                .thenReturn(Optional.of(DailySnapshot.builder()
                        .closingBalance(new BigDecimal("10000000"))
                        .build()));

        snapshotService.runDailySnapshot();

        ArgumentCaptor<DailySnapshot> captor =
                ArgumentCaptor.forClass(DailySnapshot.class);
        verify(dailyRepo, atLeastOnce()).save(captor.capture());

        DailySnapshot saved = captor.getValue();
        // closing = opening + credit - debit = 10M + 5M - 3M = 12M
        assertThat(saved.getClosingBalance()).isEqualByComparingTo("12000000");
        assertThat(saved.getOpeningBalance()).isEqualByComparingTo("10000000");
        assertThat(saved.getTotalDebit()).isEqualByComparingTo("3000000");
        assertThat(saved.getTotalCredit()).isEqualByComparingTo("5000000");
        assertThat(saved.getTxCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("runMonthlySnapshot: stores the actual transaction count")
    void runMonthlySnapshot_storesActualTransactionCount() {
        YearMonth lastMonth = YearMonth.of(2026, 6);

        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());
        when(monthlyRepo.existsByAccountIdAndYearAndMonth(
                eq(accountId),
                eq((short) lastMonth.getYear()),
                eq((short) lastMonth.getMonthValue())))
                .thenReturn(false);
        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("5000000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(8L);
        when(monthlyRepo.findByAccountIdAndYearAndMonth(any(), any(), any()))
                .thenReturn(Optional.empty());

        snapshotService.runMonthlySnapshot();

        ArgumentCaptor<MonthlySnapshot> captor =
                ArgumentCaptor.forClass(MonthlySnapshot.class);
        verify(monthlyRepo).save(captor.capture());

        MonthlySnapshot saved = captor.getValue();
        assertThat(saved.getTxCount()).isEqualTo(8);
        assertThat(saved.getClosingBalance()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("runDailySnapshot: idempotency skips account with an existing snapshot")
    void runDailySnapshot_skipsExistingSnapshot() {
        LocalDate yesterday = LocalDate.of(2026, 6, 30);

        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        // Snapshot already exists
        when(dailyRepo.existsByAccountIdAndSnapshotDate(accountId, yesterday))
                .thenReturn(true);

        snapshotService.runDailySnapshot();

        // Does not call save()
        verify(dailyRepo, never()).save(any());
    }

    @Test
    @DisplayName("runDailySnapshot: uses Ho Chi Minh business day when JVM clock is UTC")
    void runDailySnapshot_usesHoChiMinhBusinessDay() {
        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        snapshotService.runDailySnapshot();

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(txRepo).findDistinctFromAccountIdsByPeriod(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-30T00:00:00+07:00"));
        assertThat(to.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-30T23:59:59.999999999+07:00"));
    }

    @Test
    @DisplayName("runMonthlySnapshot: uses Ho Chi Minh business month when JVM clock is UTC")
    void runMonthlySnapshot_usesHoChiMinhBusinessMonth() {
        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        snapshotService.runMonthlySnapshot();

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(txRepo).findDistinctFromAccountIdsByPeriod(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00+07:00"));
        assertThat(to.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-30T23:59:59.999999999+07:00"));
    }

    @Test
    @DisplayName("runDailySnapshot: skips when there are no transactions")
    void runDailySnapshot_noTransactions_skips() {
        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        snapshotService.runDailySnapshot();

        verify(dailyRepo, never()).save(any());
    }
}
