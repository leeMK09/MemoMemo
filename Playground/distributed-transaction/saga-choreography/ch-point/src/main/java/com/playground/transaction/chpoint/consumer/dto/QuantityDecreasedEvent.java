package com.playground.transaction.chpoint.consumer.dto;

public record QuantityDecreasedEvent(Long orderId, Long totalPrice) {
}
