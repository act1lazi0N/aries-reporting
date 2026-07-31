package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitTransferCompletedListenerTest {
    @Mock
    ReportingEventConsumer reportingEventConsumer;
    @Mock
    Channel channel;

    @Test
    void onTransferCompleted_acksAfterConsumerSucceeds() throws Exception {
        RabbitTransferCompletedListener listener =
                new RabbitTransferCompletedListener(reportingEventConsumer);
        Message brokerMessage = brokerMessage(42L);
        TransferCompletedMessage transferMessage = message();

        listener.onTransferCompleted(transferMessage, brokerMessage, channel);

        verify(reportingEventConsumer).consumeTransferCompleted(transferMessage);
        verify(channel).basicAck(42L, false);
    }

    @Test
    void onTransferCompleted_rejectsInvalidMessage() throws Exception {
        RabbitTransferCompletedListener listener =
                new RabbitTransferCompletedListener(reportingEventConsumer);
        Message brokerMessage = brokerMessage(43L);
        TransferCompletedMessage transferMessage = message();
        doThrow(new IllegalArgumentException("bad event"))
                .when(reportingEventConsumer).consumeTransferCompleted(transferMessage);

        listener.onTransferCompleted(transferMessage, brokerMessage, channel);

        verify(channel).basicReject(43L, false);
    }

    @Test
    void onTransferCompleted_requeuesTransientFailure() throws Exception {
        RabbitTransferCompletedListener listener =
                new RabbitTransferCompletedListener(reportingEventConsumer);
        Message brokerMessage = brokerMessage(44L);
        TransferCompletedMessage transferMessage = message();
        doThrow(new IllegalStateException("db unavailable"))
                .when(reportingEventConsumer).consumeTransferCompleted(transferMessage);

        listener.onTransferCompleted(transferMessage, brokerMessage, channel);

        verify(channel).basicNack(44L, false, true);
    }

    private Message brokerMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }

    private TransferCompletedMessage message() {
        UUID transactionId = UUID.randomUUID();
        return new TransferCompletedMessage(
                UUID.randomUUID(),
                TransferCompletedMessage.EVENT_TYPE,
                TransferCompletedMessage.SCHEMA_VERSION,
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                new TransferCompletedMessage.Payload(
                        transactionId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Sender",
                        "Receiver",
                        "1001",
                        "2002",
                        new BigDecimal("100.00"),
                        "VND",
                        "COMPLETED",
                        "transfer",
                        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-07-01T00:00:01Z")));
    }
}
