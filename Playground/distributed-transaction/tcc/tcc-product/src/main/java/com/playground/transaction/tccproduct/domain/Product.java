package com.playground.transaction.tccproduct.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quantity;

    private Long price;

    private Long reservedQuantity;

    @Version
    private Long version;

    public Product() {
    }

    public Product(Long quantity, Long price) {
        this.quantity = quantity;
        this.price = price;
        this.reservedQuantity = 0L;
    }

    public Long calculatePrice(Long quantity) {
        return price * quantity;
    }

    public Long reserve(Long requestedQuantity) {
        long reservableQuantity = this.quantity - this.reservedQuantity;

        if (reservableQuantity < requestedQuantity) {
            throw new RuntimeException("예약할 수 있는 수량이 부족합니다");
        }

        reservedQuantity += requestedQuantity;

        return price * requestedQuantity;
    }

    public void confirm(Long requestedQuantity) {
        if (this.quantity < requestedQuantity) {
            throw new RuntimeException("재고가 부족합니다");
        }

        if (this.reservedQuantity < requestedQuantity) {
            throw new RuntimeException("예약된 수량이 부족합니다");
        }

        this.quantity -= requestedQuantity;
        this.reservedQuantity -= requestedQuantity;
    }

    public void cancel(Long requestedQuantity) {
        if (this.reservedQuantity < requestedQuantity) {
            throw new RuntimeException("예약된 수량이 부족합니다");
        }

        this.reservedQuantity -= requestedQuantity;
    }

    public void buy(Long quantity) {
        if (this.quantity < quantity) {
            throw new RuntimeException("재고가 부족합니다.");
        }

        this.quantity = this.quantity - quantity;
    }
}
