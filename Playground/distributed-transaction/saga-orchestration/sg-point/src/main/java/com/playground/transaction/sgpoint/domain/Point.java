package com.playground.transaction.sgpoint.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "points")
public class Point {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long amount;

    @Version
    private Long version;

    public Point() {
    }

    public Point(Long userId, Long amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public void use(Long amount) {
        if (this.amount < amount) {
            throw new RuntimeException("잔액이 부족합니다");
        }

        this.amount = this.amount - amount;
    }

    public Long getId() {
        return id;
    }

    public void cancel(Long amount) {
        this.amount = this.amount + amount;
    }
}
