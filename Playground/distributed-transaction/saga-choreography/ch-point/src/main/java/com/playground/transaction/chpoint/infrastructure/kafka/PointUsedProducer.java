package com.playground.transaction.chpoint.infrastructure.kafka;

import com.playground.transaction.chpoint.infrastructure.kafka.dto.PointUsedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PointUsedProducer {

    private final KafkaTemplate<String, PointUsedEvent> kafkaTemplate;

    public PointUsedProducer(KafkaTemplate<String, PointUsedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(PointUsedEvent event) {
        kafkaTemplate.send(
                "point-used",
                event.orderId().toString(),
                event
        );
    }
}
