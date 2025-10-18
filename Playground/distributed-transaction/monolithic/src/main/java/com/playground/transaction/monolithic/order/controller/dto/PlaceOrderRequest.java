package com.playground.transaction.monolithic.order.controller.dto;

import com.playground.transaction.monolithic.order.application.dto.PlaceOrderCommand;

import java.util.List;

public record PlaceOrderRequest(
        Long orderId
) {

    public PlaceOrderCommand toPlaceOrderCommand() {
        return new PlaceOrderCommand(orderId);
    }
}
