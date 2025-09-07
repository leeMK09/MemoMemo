package com.playground.notificationoutbox.notification.service.dto;

import java.time.LocalDateTime;

public record NotificationCreation(
        Long employerId,
        Long workerId,
        LocalDateTime occurredAt,
        Integer maxAttempts
) {
    public NotificationCreation(
            Long employerId,
            Long workerId
    ) {
        this(employerId, workerId, LocalDateTime.now(), 5);
    }
}
