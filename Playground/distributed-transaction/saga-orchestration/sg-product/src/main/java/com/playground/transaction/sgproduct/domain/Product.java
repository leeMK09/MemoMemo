package com.playground.transaction.sgproduct.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quantity;

    private Long price;

    @Version
    private Long version;

    public Product() {
    }

    public Product(Long quantity, Long price) {
        this.quantity = quantity;
        this.price = price;
    }

    public Long calculatePrice(Long quantity) {
        return price * quantity;
    }

    public void buy(Long quantity) {
        if (this.quantity < quantity) {
            throw new RuntimeException("재고가 부족합니다.");
        }

        this.quantity = this.quantity - quantity;
    }

    public void cancel(Long quantity) {
        this.quantity = this.quantity + quantity;
    }
}
