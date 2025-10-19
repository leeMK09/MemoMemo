package com.playground.transaction.chorder.controller.dto;

import com.playground.transaction.chorder.application.dto.PlaceOrderCommand;

public record PlaceOrderRequest(Long orderId) {

    public PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(orderId);
    }
}
