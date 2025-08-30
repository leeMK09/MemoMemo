package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.domain.NotificationRequested;
import com.playground.notificationoutbox.notification.service.dto.NotificationCreation;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final OutboxService outboxService;

    @Transactional
    public void create(NotificationCreation creation) {
        NotificationRequested notificationRequested = new NotificationRequested(
                creation.occurredAt(),
                creation.employerPhoneNumber(),
                creation.workerPhoneNumber()
        );
        outboxService.create(notificationRequested);
    }
}
