package com.playground.notificationoutbox.outbox.domain;

import java.time.LocalDateTime;

public interface IdempotencySubject {
    IdempotencyKeyType getType();

    LocalDateTime occurredAt();
}
