package com.playground.transaction.tccproduct.controller.dto;

import com.playground.transaction.tccproduct.application.dto.ProductReserveConfirmCommand;

public record ProductReserveConfirmRequest(String requestId) {

    public ProductReserveConfirmCommand toCommand() {
        return new ProductReserveConfirmCommand(requestId);
    }
}
