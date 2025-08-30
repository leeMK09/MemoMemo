package com.playground.notificationoutbox.outbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_idempotency_key", columnNames = "idempotency_key")
)
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    public Outbox(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.attemptCount = 0;
    }
}
