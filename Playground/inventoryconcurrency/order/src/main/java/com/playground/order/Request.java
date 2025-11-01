package com.playground.order;

public record Request(
        Long inventoryId,
        Integer quantity
) {
}
