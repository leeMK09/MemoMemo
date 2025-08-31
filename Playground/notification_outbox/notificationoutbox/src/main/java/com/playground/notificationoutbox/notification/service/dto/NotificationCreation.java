package com.playground.notificationoutbox.notification.service.dto;

import java.time.Instant;

public record NotificationCreation(
        Long employerId,
        Long workerId,
        Instant occurredAt,
        Integer maxAttempts
) {
    public NotificationCreation(
            Long employerId,
            Long workerId
    ) {
        this(employerId, workerId, Instant.now(), 5);
    }
}
