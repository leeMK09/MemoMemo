package com.playground.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer count = 0;

    protected Inventory() {
    }

    public Inventory(Long productId, Integer count) {
        this.productId = productId;
        this.count = count;
    }

    public void increase(Integer quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be a positive number");
        }
        this.count += quantity;
    }

    public void decrease(Integer quantity) {
        if (quantity > 0) {
            throw new IllegalArgumentException("Quantity must be a positive number");
        }
        this.count -= quantity;
    }
}
