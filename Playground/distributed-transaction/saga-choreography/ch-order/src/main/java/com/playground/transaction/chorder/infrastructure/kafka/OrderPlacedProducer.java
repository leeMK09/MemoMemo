package com.playground.transaction.chorder.infrastructure.kafka;

import com.playground.transaction.chorder.infrastructure.kafka.dto.OrderPlacedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedProducer {

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public OrderPlacedProducer(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(OrderPlacedEvent event) {
        kafkaTemplate.send(
                "order-placed",
                event.orderId().toString(),
                event
        );
    }
}
