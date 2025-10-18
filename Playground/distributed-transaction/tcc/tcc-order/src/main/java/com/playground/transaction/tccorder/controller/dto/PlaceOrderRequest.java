package com.playground.transaction.tccorder.controller.dto;

import com.playground.transaction.tccorder.application.dto.PlaceOrderCommand;

public record PlaceOrderRequest(
        Long orderId
) {

    public PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(orderId);
    }
}
