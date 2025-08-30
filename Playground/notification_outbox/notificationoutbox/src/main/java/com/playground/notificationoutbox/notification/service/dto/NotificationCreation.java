package com.playground.notificationoutbox.notification.service.dto;

import java.time.Instant;

public record NotificationCreation(
        String employerPhoneNumber,
        String workerPhoneNumber,
        Instant occurredAt
) {
    public NotificationCreation(
            String employerPhoneNumber,
            String workerPhoneNumber
    ) {
        this(employerPhoneNumber, workerPhoneNumber, Instant.now());
    }
}
