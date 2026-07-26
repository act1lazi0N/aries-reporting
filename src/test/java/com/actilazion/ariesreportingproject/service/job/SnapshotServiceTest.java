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

        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("5000000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(4L);

        // Opening = 10,000,000 from the day before yesterday
        when(dailyRepo.findFirstByAccountIdAndSnapshotDateBeforeOrderBySnapshotDateDesc(any(), eq(yesterday)))
                .thenReturn(Optional.of(DailySnapshot.builder()
                        .closingBalance(new BigDecimal("10000000"))
                        .build()));
        when(dailyRepo.findByAccountIdAndSnapshotDate(any(), eq(yesterday)))
                .thenReturn(Optional.empty());

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
        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("5000000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(8L);
        when(monthlyRepo.findLatestBeforeMonth(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
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
    @DisplayName("runDailySnapshot: idempotency updates an existing snapshot")
    void runDailySnapshot_updatesExistingSnapshot() {
        LocalDate yesterday = LocalDate.of(2026, 6, 30);
        DailySnapshot existing = DailySnapshot.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .snapshotDate(yesterday)
                .openingBalance(BigDecimal.ZERO)
                .closingBalance(BigDecimal.ZERO)
                .totalDebit(BigDecimal.ZERO)
                .totalCredit(BigDecimal.ZERO)
                .txCount(0)
                .build();

        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("1000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("2500000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(3L);
        when(dailyRepo.findFirstByAccountIdAndSnapshotDateBeforeOrderBySnapshotDateDesc(accountId, yesterday))
                .thenReturn(Optional.of(DailySnapshot.builder()
                        .closingBalance(new BigDecimal("7000000"))
                        .build()));
        when(dailyRepo.findByAccountIdAndSnapshotDate(accountId, yesterday))
                .thenReturn(Optional.of(existing));

        snapshotService.runDailySnapshot();

        verify(dailyRepo).save(existing);
        assertThat(existing.getOpeningBalance()).isEqualByComparingTo("7000000");
        assertThat(existing.getClosingBalance()).isEqualByComparingTo("8500000");
        assertThat(existing.getTxCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("runDailySnapshot: carries latest prior balance across inactive days")
    void runDailySnapshot_carriesLatestPriorBalanceAcrossGap() {
        LocalDate yesterday = LocalDate.of(2026, 6, 30);

        when(txRepo.findDistinctFromAccountIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));
        when(txRepo.findDistinctToAccountIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());
        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("500000"));
        when(txRepo.countByAccountAndPeriod(any(), any(), any()))
                .thenReturn(1L);
        when(dailyRepo.findFirstByAccountIdAndSnapshotDateBeforeOrderBySnapshotDateDesc(accountId, yesterday))
                .thenReturn(Optional.of(DailySnapshot.builder()
                        .snapshotDate(LocalDate.of(2026, 6, 20))
                        .closingBalance(new BigDecimal("9000000"))
                        .build()));
        when(dailyRepo.findByAccountIdAndSnapshotDate(accountId, yesterday))
                .thenReturn(Optional.empty());

        snapshotService.runDailySnapshot();

        ArgumentCaptor<DailySnapshot> captor = ArgumentCaptor.forClass(DailySnapshot.class);
        verify(dailyRepo).save(captor.capture());
        assertThat(captor.getValue().getOpeningBalance()).isEqualByComparingTo("9000000");
        assertThat(captor.getValue().getClosingBalance()).isEqualByComparingTo("9500000");
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
