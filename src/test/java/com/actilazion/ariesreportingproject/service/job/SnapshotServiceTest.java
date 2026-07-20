package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.entity.reporting.DailySnapshot;
import com.actilazion.ariesreportingproject.repository.reporting.DailySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.MonthlySnapshotRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @InjectMocks
    SnapshotService snapshotService;

    private final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("runDailySnapshot: creates snapshot with the correct balance")
    void runDailySnapshot_createsCorrectSnapshot() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Account had transactions yesterday
        when(txRepo.findSyncedTxIdsByPeriod(any(), any()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        when(dailyRepo.existsByAccountIdAndSnapshotDate(any(), eq(yesterday)))
                .thenReturn(false);

        when(txRepo.sumDebitByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("3000000"));
        when(txRepo.sumCreditByAccountAndPeriod(any(), any(), any()))
                .thenReturn(new BigDecimal("5000000"));

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
    }

    @Test
    @DisplayName("runDailySnapshot: idempotency skips account with an existing snapshot")
    void runDailySnapshot_skipsExistingSnapshot() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        when(txRepo.findSyncedTxIdsByPeriod(any(), any()))
                .thenReturn(List.of(accountId));

        // Snapshot already exists
        when(dailyRepo.existsByAccountIdAndSnapshotDate(accountId, yesterday))
                .thenReturn(true);

        snapshotService.runDailySnapshot();

        // Does not call save()
        verify(dailyRepo, never()).save(any());
    }

    @Test
    @DisplayName("runDailySnapshot: skips when there are no transactions")
    void runDailySnapshot_noTransactions_skips() {
        when(txRepo.findSyncedTxIdsByPeriod(any(), any()))
                .thenReturn(Collections.emptyList());

        snapshotService.runDailySnapshot();

        verify(dailyRepo, never()).save(any());
    }
}
