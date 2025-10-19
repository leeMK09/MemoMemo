package com.playground.transaction.chproduct.infrastructure.kafka.dto;

public record QuantityDecreasedEvent(Long orderId, Long totalPrice) {
}
