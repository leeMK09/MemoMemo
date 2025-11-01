package com.playground.order;

public record CreateRequest(
        Long inventoryId,
        Integer quantity
) {
}
