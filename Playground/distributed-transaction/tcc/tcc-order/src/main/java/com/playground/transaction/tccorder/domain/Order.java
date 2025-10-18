package com.playground.transaction.tccorder.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order() {
        status = OrderStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void complete() {
        status = OrderStatus.COMPLETED;
    }

    public void reserve() {
        if (this.status != OrderStatus.CREATED) {
            throw new RuntimeException("생성된 주문에서만 예약할 수 있습니다");
        }
        this.status = OrderStatus.RESERVED;
    }

    public void cancel() {
        if (this.status != OrderStatus.RESERVED) {
            throw new RuntimeException("예약 중에서만 취소가 가능합니다.");
        }

        this.status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        if (this.status != OrderStatus.RESERVED && this.status != OrderStatus.PENDING) {
            throw new RuntimeException("예약 혹은 Pending 에서만 확정할 수 있습니다.");
        }

        this.status = OrderStatus.CONFIRMED;
    }

    public void pending() {
        if (this.status != OrderStatus.RESERVED) {
            throw new RuntimeException("예약 중에서만 확정할 수 있습니다.");
        }

        this.status = OrderStatus.PENDING;
    }

    public enum OrderStatus {
        CREATED, RESERVED, CANCELLED, CONFIRMED, PENDING, COMPLETED
    }
}
