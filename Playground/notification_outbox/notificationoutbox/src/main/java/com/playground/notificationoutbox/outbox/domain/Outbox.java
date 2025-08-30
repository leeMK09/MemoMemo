package com.playground.notificationoutbox.outbox.domain;

import jakarta.persistence.*;

@Table(name = "outbox")
@Entity
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;
}
