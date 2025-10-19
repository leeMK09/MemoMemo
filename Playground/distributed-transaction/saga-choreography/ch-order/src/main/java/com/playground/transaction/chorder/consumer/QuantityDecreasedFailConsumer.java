package com.playground.transaction.chorder.consumer;

import com.playground.transaction.chorder.application.OrderService;
import com.playground.transaction.chorder.consumer.dto.QuantityDecreasedFailEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class QuantityDecreasedFailConsumer {

    private final OrderService orderService;

    public QuantityDecreasedFailConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = "quantity-decreased-fail",
            groupId = "quantity-decreased-fail-consumer",
            properties = "com.playground.transaction.chorder.consumer.dto.QuantityDecreasedFailEvent"
    )
    public void handle(QuantityDecreasedFailEvent event) {
//        orderService.fail(event.orderId());
    }
}
