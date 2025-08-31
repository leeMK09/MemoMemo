package com.playground.notificationoutbox.outbox.service.dto;

public record OutboxResult(
        String idempotencyKey
) {
}
