package com.playground.notificationoutbox.outbox.domain;

public interface IdempotencyKeyGenerator<T extends IdempotencySubject> {
    boolean support(IdempotencyKeyType keyType);

    String generate(T subject);
}
