package com.playground.transaction.chproduct.infrastructure.kafka;

import com.playground.transaction.chproduct.infrastructure.kafka.dto.QuantityDecreasedFailEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuantityDecreasedFailProducer {

    private final KafkaTemplate<String, QuantityDecreasedFailEvent> kafkaTemplate;

    public QuantityDecreasedFailProducer(KafkaTemplate<String, QuantityDecreasedFailEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(QuantityDecreasedFailEvent event) {
        kafkaTemplate.send(
                "quantity-decreased-fail",
                event.orderId().toString(),
                event
        );
    }
}
