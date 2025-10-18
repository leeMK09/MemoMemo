package com.playground.transaction.tccproduct.controller.dto;

import com.playground.transaction.tccproduct.application.dto.ProductReserveCancelCommand;

public record ProductReserveCancelRequest(String requestId) {

    public ProductReserveCancelCommand toCommand() {
        return new ProductReserveCancelCommand(requestId);
    }
}
