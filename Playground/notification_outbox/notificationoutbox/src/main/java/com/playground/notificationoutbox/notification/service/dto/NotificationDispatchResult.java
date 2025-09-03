package com.playground.notificationoutbox.notification.service.dto;

import com.playground.notificationoutbox.outbox.domain.OutboxStatus;

import java.time.LocalDateTime;

public sealed interface NotificationDispatchResult {
    record Success(Long outboxId, OutboxStatus status) implements NotificationDispatchResult {}
    record Failure(Long outboxId, OutboxStatus status, LocalDateTime nextAttemptAt, Integer attemptCount) implements NotificationDispatchResult {}

    static Success success(Long outboxId, OutboxStatus status) {
        return new Success(outboxId, status);
    }

    static Failure failure(Long outboxId, OutboxStatus status, LocalDateTime nextAttemptAt, Integer attemptCount) {
        return new Failure(outboxId, status, nextAttemptAt, attemptCount);
    }

    static boolean isSuccess(NotificationDispatchResult result) {
        return result instanceof Success;
    }
}
