package com.playground.notificationoutbox.notification.service.dto;

import java.time.LocalDateTime;

public sealed interface NotificationDispatchResult {
    record Success(Long outboxId) implements NotificationDispatchResult {}
    record Failure(Long outboxId, LocalDateTime nextAttemptAt) implements NotificationDispatchResult {}

    static Success success(Long outboxId) {
        return new Success(outboxId);
    }

    static Failure failure(Long outboxId, LocalDateTime nextAttemptAt) {
        return new Failure(outboxId, nextAttemptAt);
    }

    static boolean isSuccess(NotificationDispatchResult result) {
        return result instanceof Success;
    }
}
