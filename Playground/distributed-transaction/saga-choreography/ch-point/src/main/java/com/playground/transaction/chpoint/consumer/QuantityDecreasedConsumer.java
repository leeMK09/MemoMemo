package com.playground.transaction.chpoint.consumer;

import com.playground.transaction.chpoint.application.PointService;
import com.playground.transaction.chpoint.application.dto.PointUseCancelCommand;
import com.playground.transaction.chpoint.application.dto.PointUseCommand;
import com.playground.transaction.chpoint.consumer.dto.QuantityDecreasedEvent;
import com.playground.transaction.chpoint.infrastructure.kafka.PointUseFailProducer;
import com.playground.transaction.chpoint.infrastructure.kafka.PointUsedProducer;
import com.playground.transaction.chpoint.infrastructure.kafka.dto.PointUseFailEvent;
import com.playground.transaction.chpoint.infrastructure.kafka.dto.PointUsedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class QuantityDecreasedConsumer {

    private final PointService pointService;
    private final PointUsedProducer pointUsedProducer;
    private final PointUseFailProducer pointUseFailProducer;

    public QuantityDecreasedConsumer(PointService pointService, PointUsedProducer pointUsedProducer, PointUseFailProducer pointUseFailProducer) {
        this.pointService = pointService;
        this.pointUsedProducer = pointUsedProducer;
        this.pointUseFailProducer = pointUseFailProducer;
    }

    @KafkaListener(
            topics = "quantity-decreased",
            groupId = "quantity-decreased-consumer",
            properties = {
                    "spring.json.value.default.type=com.playground.transaction.chpoint.consumer.dto.QuantityDecreasedEvent"
            }
    )
    public void handle(QuantityDecreasedEvent event) {
        String requestId = event.orderId().toString();
        try {
            pointService.use(new PointUseCommand(requestId, 1L, event.totalPrice()));

            pointUsedProducer.send(new PointUsedEvent(event.orderId()));
        } catch (Exception e) {
            pointService.cancel(new PointUseCancelCommand(requestId));

            pointUseFailProducer.send(new PointUseFailEvent(event.orderId()));
        }
    }
}
