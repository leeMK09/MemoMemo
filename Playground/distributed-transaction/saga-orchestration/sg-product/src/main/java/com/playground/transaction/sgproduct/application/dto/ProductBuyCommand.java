package com.playground.transaction.sgproduct.application.dto;

import java.util.List;

public record ProductBuyCommand(
        String requestId,
        List<ProductInfo> productInfos
) {

    public record ProductInfo(
            Long productId,
            Long quantity
    ) {};
}
