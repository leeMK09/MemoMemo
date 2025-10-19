package com.playground.transaction.chproduct.consumer;

import com.playground.transaction.chproduct.application.ProductService;
import com.playground.transaction.chproduct.application.dto.ProductBuyCancelCommand;
import com.playground.transaction.chproduct.application.dto.ProductBuyCommand;
import com.playground.transaction.chproduct.application.dto.ProductBuyResult;
import com.playground.transaction.chproduct.consumer.dto.OrderPlacedEvent;
import com.playground.transaction.chproduct.infrastructure.kafka.QuantityDecreasedFailProducer;
import com.playground.transaction.chproduct.infrastructure.kafka.QuantityDecreasedProducer;
import com.playground.transaction.chproduct.infrastructure.kafka.dto.QuantityDecreasedEvent;
import com.playground.transaction.chproduct.infrastructure.kafka.dto.QuantityDecreasedFailEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedConsumer {

    private final ProductService productService;
    private final QuantityDecreasedProducer quantityDecreasedProducer;
    private final QuantityDecreasedFailProducer quantityDecreasedFailProducer;

    public OrderPlacedConsumer(ProductService productService, QuantityDecreasedProducer quantityDecreasedProducer, QuantityDecreasedFailProducer quantityDecreasedFailProducer) {
        this.productService = productService;
        this.quantityDecreasedProducer = quantityDecreasedProducer;
        this.quantityDecreasedFailProducer = quantityDecreasedFailProducer;
    }

    @KafkaListener(
            topics = "order-placed",
            groupId = "order-placed-consumer",
            properties = {
                    "spring.json.value.default.type=com.playground.transaction.chproduct.consumer.dto.OrderPlacedEvent"
            }
    )
    public void handle(OrderPlacedEvent event) {
        String requestId = event.orderId().toString();

        try {
            ProductBuyResult result = productService.buy(
                    new ProductBuyCommand(
                            requestId,
                            event.productInfos()
                                    .stream()
                                    .map(item -> new ProductBuyCommand.ProductInfo(item.productId(), item.quantity()))
                                    .toList()
                    )
            );

            quantityDecreasedProducer.send(
                    new QuantityDecreasedEvent(
                            event.orderId(),
                            result.totalPrice()
                    )
            );
        } catch (Exception e) {
            productService.cancel(
                    new ProductBuyCancelCommand(requestId)
            );

            quantityDecreasedFailProducer.send(
                    new QuantityDecreasedFailEvent(event.orderId())
            );
        }
    }
}
