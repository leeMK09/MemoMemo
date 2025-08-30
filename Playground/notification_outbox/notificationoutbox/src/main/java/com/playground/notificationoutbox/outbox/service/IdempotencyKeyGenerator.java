package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import com.playground.notificationoutbox.outbox.domain.IdempotencySubject;

public interface IdempotencyKeyGenerator<T extends IdempotencySubject> {
    boolean support(IdempotencyKeyType keyType);

    String generate(T subject);
}
