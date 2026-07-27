package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.TransactionViewRepository;
import com.actilazion.ariesreportingproject.service.sync.BackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
    @Mock
    TransactionViewRepository txViewRepo;
    @Mock
    ReportingTransactionRepository reportingRepo;
    @Mock
    BackfillService backfillService;
    @InjectMocks
    ReconciliationService reconciliationService;

    @Test
    @DisplayName("runReconciliation: does not trigger backfill when counts match")
    void runReconciliation_noMismatch_noBackfill() {
        UUID txId = UUID.randomUUID();
        when(txViewRepo.findIdsByCreatedAtAfter(any())).thenReturn(List.of(txId));
        when(reportingRepo.findSyncedTxIdsByCreatedAtAfter(any())).thenReturn(List.of(txId));

        reconciliationService.runReconciliation();

        verify(backfillService, never()).backfill(any());
    }

    @Test
    @DisplayName("runReconciliation: triggers backfill when a mismatch is detected")
    void runReconciliation_mismatch_triggersBackfill() {
        UUID txId = UUID.randomUUID();
        when(txViewRepo.findIdsByCreatedAtAfter(any())).thenReturn(List.of(txId));
        when(reportingRepo.findSyncedTxIdsByCreatedAtAfter(any())).thenReturn(List.of());
        when(backfillService.backfill(any(OffsetDateTime.class))).thenReturn(5L);

        reconciliationService.runReconciliation();

        verify(backfillService).backfill(any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("runReconciliation: reporting exceeds source, so no backfill for phantom data")
    void runReconciliation_reportingExceedsSource_noBackfill() {
        UUID phantomId = UUID.randomUUID();
        when(txViewRepo.findIdsByCreatedAtAfter(any())).thenReturn(List.of());
        when(reportingRepo.findSyncedTxIdsByCreatedAtAfter(any())).thenReturn(List.of(phantomId));

        reconciliationService.runReconciliation();

        // Reporting exceeds the source, which indicates phantom data that needs manual investigation.
        // Backfill cannot resolve this case.
        verify(backfillService, never()).backfill(any());
    }

    @Test
    @DisplayName("runReconciliation: triggers backfill when counts match but ids differ")
    void runReconciliation_equalCountsDifferentIds_triggersBackfill() {
        when(txViewRepo.findIdsByCreatedAtAfter(any())).thenReturn(List.of(UUID.randomUUID()));
        when(reportingRepo.findSyncedTxIdsByCreatedAtAfter(any())).thenReturn(List.of(UUID.randomUUID()));
        when(backfillService.backfill(any(OffsetDateTime.class))).thenReturn(1L);

        reconciliationService.runReconciliation();

        verify(backfillService).backfill(any(OffsetDateTime.class));
    }
}
