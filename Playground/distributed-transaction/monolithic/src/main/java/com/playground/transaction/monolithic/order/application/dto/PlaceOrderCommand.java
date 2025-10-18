package com.playground.transaction.monolithic.order.application.dto;

import java.util.List;

public record PlaceOrderCommand(
        Long orderId
) {
}
