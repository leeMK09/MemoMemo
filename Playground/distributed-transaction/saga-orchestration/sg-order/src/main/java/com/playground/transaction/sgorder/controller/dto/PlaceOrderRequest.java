package com.playground.transaction.sgorder.controller.dto;

import com.playground.transaction.sgorder.application.dto.PlaceOrderCommand;

public record PlaceOrderRequest(Long orderId) {

    public PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(orderId);
    }
}
