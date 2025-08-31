package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {
    private final OutboxService outboxService;
    private final NotificationDispatcher notificationDispatcher;

    @Value("${outbox.batch:50}")
    private int batchSize;

    @Scheduled(fixedRate = 5000)
    public void send() {
        List<Outbox> outboxes = outboxService.resolveConsumables(batchSize);
        if (outboxes.isEmpty()) return;

        notificationDispatcher.dispatch(outboxes);
    }
}
