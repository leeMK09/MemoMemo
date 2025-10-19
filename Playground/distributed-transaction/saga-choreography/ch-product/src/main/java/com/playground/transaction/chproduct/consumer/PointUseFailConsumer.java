package com.playground.transaction.chproduct.consumer;

import com.playground.transaction.chproduct.application.ProductService;
import com.playground.transaction.chproduct.consumer.dto.PointUseFailEvent;
import com.playground.transaction.chproduct.infrastructure.kafka.QuantityDecreasedFailProducer;
import com.playground.transaction.chproduct.infrastructure.kafka.dto.QuantityDecreasedFailEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PointUseFailConsumer {

    private final ProductService productService;

    private final QuantityDecreasedFailProducer quantityDecreasedFailProducer;

    public PointUseFailConsumer(ProductService productService, QuantityDecreasedFailProducer quantityDecreasedFailProducer) {
        this.productService = productService;
        this.quantityDecreasedFailProducer = quantityDecreasedFailProducer;
    }

    @KafkaListener(
            topics = "point-use-fail",
            groupId = "point-use-fail-consumer",
            properties = {
                    "spring.json.value.default.type=com.playground.transaction.chproduct.consumer.dto.PointUseFailEvent"
            }
    )
    public void handle(PointUseFailEvent event) {
//        productService.cancel();
        quantityDecreasedFailProducer.send(new QuantityDecreasedFailEvent(event.orderId()));
    }
}
