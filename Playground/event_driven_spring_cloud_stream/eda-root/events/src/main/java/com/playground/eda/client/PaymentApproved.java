package com.playground.eda.client;

public record PaymentApproved(
        Long orderId,
        Long userId,
        int amount,
        String txId
) {
}
