package com.playground.transaction.sgpoint.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "point_transaction_histories")
public class PointTransactionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    private String requestId;

    private Long pointId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    public PointTransactionHistory() {
    }

    public PointTransactionHistory(String requestId, Long pointId, Long amount, TransactionType transactionType) {
        this.requestId = requestId;
        this.pointId = pointId;
        this.amount = amount;
        this.transactionType = transactionType;
    }

    public Long getPointId() {
        return pointId;
    }

    public Long getAmount() {
        return amount;
    }
}
