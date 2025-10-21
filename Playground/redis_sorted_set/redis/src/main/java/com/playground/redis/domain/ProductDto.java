package com.playground.redis.domain;

public record ProductDto(
        Long id,
        String name,
        String description,
        Integer price
) {
    public static ProductDto from(Product product) {
        return new ProductDto(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }
}
