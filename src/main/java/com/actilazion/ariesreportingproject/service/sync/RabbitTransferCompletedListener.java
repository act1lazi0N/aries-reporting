package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.dto.integration.TransferCompletedMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.reporting.events.rabbit", name = "enabled", havingValue = "true")
public class RabbitTransferCompletedListener {
    private final ReportingEventConsumer reportingEventConsumer;

    @RabbitListener(
            queues = "${app.reporting.events.rabbit.transfer-completed-queue}",
            ackMode = "MANUAL")
    public void onTransferCompleted(
            TransferCompletedMessage transferMessage,
            Message brokerMessage,
            Channel channel) throws IOException {
        long deliveryTag = brokerMessage.getMessageProperties().getDeliveryTag();
        try {
            reportingEventConsumer.consumeTransferCompleted(transferMessage);
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException ex) {
            channel.basicReject(deliveryTag, false);
            log.warn("[SYNC] Rejected invalid transfer event deliveryTag={} - {}",
                    deliveryTag, ex.getMessage());
        } catch (Exception ex) {
            channel.basicNack(deliveryTag, false, true);
            log.warn("[SYNC] Requeued transfer event deliveryTag={} - {}",
                    deliveryTag, ex.getMessage());
            log.debug("[SYNC] Rabbit transfer event failure", ex);
        }
    }
}
