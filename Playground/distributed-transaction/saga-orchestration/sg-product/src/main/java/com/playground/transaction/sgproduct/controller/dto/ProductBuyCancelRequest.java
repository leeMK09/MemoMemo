package com.playground.transaction.sgproduct.controller.dto;

import com.playground.transaction.sgproduct.application.dto.ProductBuyCancelCommand;

public record ProductBuyCancelRequest(String requestId) {

    public ProductBuyCancelCommand toCommand() {
        return new ProductBuyCancelCommand(requestId);
    }
}
