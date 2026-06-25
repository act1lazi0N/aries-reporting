package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.event.TransferCompletedEvent;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportingSyncListenerTest {
    @Mock
    ReportingTransactionRepository repo;
    @InjectMocks
    ReportingSyncListener listener;

    @Test
    @DisplayName("onTransferCompleted: persists ReportingTransaction with the correct fields")
    void onTransferCompleted_persistsCorrectly() {
        UUID txId = UUID.randomUUID();
        when(repo.existsByOriginalTxId(txId)).thenReturn(false);

        TransferCompletedEvent event = buildEvent(txId);
        listener.onTransferCompleted(event);

        ArgumentCaptor<ReportingTransaction> captor =
                ArgumentCaptor.forClass(ReportingTransaction.class);
        verify(repo).save(captor.capture());

        ReportingTransaction saved = captor.getValue();
        assertThat(saved.getOriginalTxId()).isEqualTo(txId);
        assertThat(saved.getAmount()).isEqualByComparingTo("1000000");
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        // Pre-computed fields
        assertThat(saved.getDayOfWeek()).isBetween((short) 1, (short) 7);
        assertThat(saved.getHourOfDay()).isBetween((short) 0, (short) 23);
    }

    @Test
    @DisplayName("onTransferCompleted: skips when originalTxId already exists (idempotency)")
    void onTransferCompleted_skipsDuplicate() {
        UUID txId = UUID.randomUUID();
        when(repo.existsByOriginalTxId(txId)).thenReturn(true);

        listener.onTransferCompleted(buildEvent(txId));

        // Does not call save() for duplicates
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("onTransferCompleted: exception does not propagate or affect caller")
    void onTransferCompleted_exceptionDoesNotPropagate() {
        UUID txId = UUID.randomUUID();
        when(repo.existsByOriginalTxId(txId)).thenReturn(false);
        when(repo.save(any())).thenThrow(new RuntimeException("DB error"));

        // Does not throw outside the listener
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> listener.onTransferCompleted(buildEvent(txId)));
    }

    private TransferCompletedEvent buildEvent(UUID txId) {
        return new TransferCompletedEvent(
                txId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Nguyen Van A", "Tran Thi B",
                "ACC000001", "ACC000002",
                new BigDecimal("1000000"), "VND", "COMPLETED",
                "Test transfer",
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }
}
