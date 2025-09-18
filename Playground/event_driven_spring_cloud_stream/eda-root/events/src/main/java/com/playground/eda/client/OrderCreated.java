package com.playground.eda.client;

public record OrderCreated(
        Long orderId,
        Long userId,
        int amount
) {
}
