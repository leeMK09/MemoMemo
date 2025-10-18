package com.playground.transaction.tccorder.application.dto;

import java.util.List;

public record OrderDto(
        List<OrderItem> orderItems
) {

    public record OrderItem(
            Long productId,
            Long quantity
    ) {}
}
