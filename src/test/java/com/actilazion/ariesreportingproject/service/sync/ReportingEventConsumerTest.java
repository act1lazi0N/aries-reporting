package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.actilazion.ariesreportingproject.entity.reporting.ProcessedEvent;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.repository.reporting.ProcessedEventRepository;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingEventConsumerTest {
    @Mock
    ReportingTransactionRepository reportingTransactionRepository;
    @Mock
    ProcessedEventRepository processedEventRepository;
    ReportingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReportingEventConsumer(
                reportingTransactionRepository,
                processedEventRepository,
                new TransferCompletedMessageMapper());
    }

    @Test
    @DisplayName("consumeTransferCompleted: first delivery projects transaction and marks event processed")
    void consumeTransferCompleted_firstDelivery_projectsTransactionAndMarksProcessed() {
        TransferCompletedMessage message = buildMessage();
        when(processedEventRepository.existsByEventId(message.eventId())).thenReturn(false);
        when(reportingTransactionRepository.existsByOriginalTxId(message.aggregateId())).thenReturn(false);

        consumer.consumeTransferCompleted(message);

        ArgumentCaptor<ReportingTransaction> transactionCaptor =
                ArgumentCaptor.forClass(ReportingTransaction.class);
        verify(reportingTransactionRepository).save(transactionCaptor.capture());
        ReportingTransaction saved = transactionCaptor.getValue();
        assertThat(saved.getOriginalTxId()).isEqualTo(message.aggregateId());
        assertThat(saved.getAmount()).isEqualByComparingTo("1000000");
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getDayOfWeek()).isBetween((short) 1, (short) 7);
        assertThat(saved.getHourOfDay()).isBetween((short) 0, (short) 23);

        ArgumentCaptor<ProcessedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        ProcessedEvent processed = eventCaptor.getValue();
        assertThat(processed.getEventId()).isEqualTo(message.eventId());
        assertThat(processed.getAggregateId()).isEqualTo(message.aggregateId());
        assertThat(processed.getEventType()).isEqualTo(TransferCompletedMessage.EVENT_TYPE);
        assertThat(processed.getSchemaVersion()).isEqualTo(TransferCompletedMessage.SCHEMA_VERSION);
    }

    @Test
    @DisplayName("consumeTransferCompleted: duplicate eventId is skipped")
    void consumeTransferCompleted_duplicateEventId_skips() {
        TransferCompletedMessage message = buildMessage();
        when(processedEventRepository.existsByEventId(message.eventId())).thenReturn(true);

        consumer.consumeTransferCompleted(message);

        verify(reportingTransactionRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("consumeTransferCompleted: existing projection still records event acknowledgement")
    void consumeTransferCompleted_existingProjection_marksEventProcessed() {
        TransferCompletedMessage message = buildMessage();
        when(processedEventRepository.existsByEventId(message.eventId())).thenReturn(false);
        when(reportingTransactionRepository.existsByOriginalTxId(message.aggregateId())).thenReturn(true);

        consumer.consumeTransferCompleted(message);

        verify(reportingTransactionRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("consumeTransferCompleted: rejects unsupported event type")
    void consumeTransferCompleted_unsupportedType_throws() {
        TransferCompletedMessage message = new TransferCompletedMessage(
                UUID.randomUUID(),
                "transaction.transfer.reversed",
                TransferCompletedMessage.SCHEMA_VERSION,
                OffsetDateTime.now(),
                buildMessage().payload());

        assertThatThrownBy(() -> consumer.consumeTransferCompleted(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported eventType: transaction.transfer.reversed");
    }

    @Test
    @DisplayName("consumeTransferCompleted: rejects unsupported schema version")
    void consumeTransferCompleted_unsupportedSchema_throws() {
        TransferCompletedMessage message = new TransferCompletedMessage(
                UUID.randomUUID(),
                TransferCompletedMessage.EVENT_TYPE,
                2,
                OffsetDateTime.now(),
                buildMessage().payload());

        assertThatThrownBy(() -> consumer.consumeTransferCompleted(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported schemaVersion: 2");
    }

    private TransferCompletedMessage buildMessage() {
        UUID txId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-20T10:15:30Z");
        return new TransferCompletedMessage(
                UUID.randomUUID(),
                TransferCompletedMessage.EVENT_TYPE,
                TransferCompletedMessage.SCHEMA_VERSION,
                createdAt,
                new TransferCompletedMessage.Payload(
                        txId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Nguyen Van A",
                        "Tran Thi B",
                        "ACC000001",
                        "ACC000002",
                        new BigDecimal("1000000"),
                        "VND",
                        "COMPLETED",
                        "Test transfer",
                        createdAt,
                        createdAt.plusSeconds(5)
                )
        );
    }
}
