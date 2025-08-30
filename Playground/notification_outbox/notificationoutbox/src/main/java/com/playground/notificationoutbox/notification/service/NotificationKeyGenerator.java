package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.domain.NotificationRequested;
import com.playground.notificationoutbox.outbox.service.IdempotencyKeyGenerator;
import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import org.springframework.stereotype.Component;

@Component
public class NotificationKeyGenerator implements IdempotencyKeyGenerator<NotificationRequested> {
    private final String delimiter = "_";

    @Override
    public boolean support(IdempotencyKeyType keyType) {
        return keyType.equals(IdempotencyKeyType.NOTIFICATION);
    }

    @Override
    public String generate(NotificationRequested subject) {
        return subject.employerPhoneNumber() + delimiter + subject.workerPhoneNumber() + delimiter + subject.occurredAt();
    }
}
