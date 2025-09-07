package com.playground.notificationoutbox.notification.domain;

import com.playground.notificationoutbox.outbox.domain.Channel;
import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import com.playground.notificationoutbox.outbox.domain.IdempotencySubject;

import java.time.Instant;

public record NotificationRequested(
        Instant occurredAt,
        String employerPhoneNumber,
        String workerPhoneNumber,
        Channel channel
) implements IdempotencySubject {
    @Override
    public IdempotencyKeyType getType() {
        return IdempotencyKeyType.NOTIFICATION;
    }
}
