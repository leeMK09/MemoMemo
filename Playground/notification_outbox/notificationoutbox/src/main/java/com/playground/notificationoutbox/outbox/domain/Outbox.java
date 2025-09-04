package com.playground.notificationoutbox.outbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_idempotency_key", columnNames = "idempotency_key")
)
@Getter
public class Outbox {
    private static final long DEFAULT_OFFSET_ATTEMPT_AT = 5L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.NEW;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    public Outbox(String idempotencyKey, Integer maxAttempts) {
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(DEFAULT_OFFSET_ATTEMPT_AT);
        this.attemptCount = 0;
        this.status = OutboxStatus.NEW;
    }

    public Outbox(String idempotencyKey, Integer maxAttempts, LocalDateTime nextAttemptAt) {
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount = 0;
        this.status = OutboxStatus.NEW;
    }

    public void sent() {
        this.status = OutboxStatus.PROCESSING;
        this.attemptCount++;
    }

    public void completed() {
        this.status = OutboxStatus.COMPLETED;
    }

    public void failed() {
        this.status = OutboxStatus.FAILED;

        if (this.isMaxReached()) {
            this.dead();
        }
    }

    public boolean isMaxReached() {
        return attemptCount >= maxAttempts;
    }

    private void dead() {
        this.status = OutboxStatus.DEAD;
    }
}
