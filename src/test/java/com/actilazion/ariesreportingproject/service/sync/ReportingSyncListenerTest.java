package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.actilazion.ariesreportingproject.event.TransferCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
public class ReportingSyncListenerTest {
    @Mock
    TransferCompletedMessageMapper mapper;
    @Mock
    ReportingEventConsumer reportingEventConsumer;
    @InjectMocks
    ReportingSyncListener listener;

    @Test
    @DisplayName("onTransferCompleted: adapts legacy event into integration consumer")
    void onTransferCompleted_adaptsLegacyEvent() {
        UUID txId = UUID.randomUUID();
        TransferCompletedEvent event = buildEvent(txId);
        TransferCompletedMessage message = buildMessage(txId);
        when(mapper.fromLegacyEvent(event)).thenReturn(message);

        listener.onTransferCompleted(event);

        verify(reportingEventConsumer).consumeTransferCompleted(message);
    }

    @Test
    @DisplayName("onTransferCompleted: consumer exception does not propagate")
    void onTransferCompleted_exceptionDoesNotPropagate() {
        UUID txId = UUID.randomUUID();
        TransferCompletedEvent event = buildEvent(txId);
        TransferCompletedMessage message = buildMessage(txId);
        when(mapper.fromLegacyEvent(event)).thenReturn(message);
        doThrow(new RuntimeException("DB error"))
                .when(reportingEventConsumer).consumeTransferCompleted(message);

        assertDoesNotThrow(() -> listener.onTransferCompleted(event));
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

    private TransferCompletedMessage buildMessage(UUID txId) {
        return new TransferCompletedMessage(
                UUID.randomUUID(),
                TransferCompletedMessage.EVENT_TYPE,
                TransferCompletedMessage.SCHEMA_VERSION,
                OffsetDateTime.now(),
                new TransferCompletedMessage.Payload(
                        txId,
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "Nguyen Van A", "Tran Thi B",
                        "ACC000001", "ACC000002",
                        new BigDecimal("1000000"), "VND", "COMPLETED",
                        "Test transfer",
                        OffsetDateTime.now(), OffsetDateTime.now()
                )
        );
    }
}
