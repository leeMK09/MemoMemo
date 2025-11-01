package com.playground.order;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long inventoryId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected Order() {
    }

    public Order(Long inventoryId, Integer quantity) {
        this.inventoryId = inventoryId;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void delete() {
        this.status = OrderStatus.DELETED;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    enum OrderStatus {
        CREATED, CANCELLED, DELETED
    }
}
