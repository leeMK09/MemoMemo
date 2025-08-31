package com.playground.notificationoutbox.notification.controller.dto;

public record NotificationRequest(
        Long employerId,
        Long workerId
) {
}
