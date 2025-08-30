package com.playground.notificationoutbox.outbox.domain;

import java.time.Instant;

public interface IdempotencySubject {
    IdempotencyKeyType getType();

    Instant occurredAt();
}
