package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.service.OutboxReader;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationConsumerScheduler {
    private final OutboxReader outboxReader;

    @Scheduled(fixedRate = 5000)
    public void send() {
        List<Outbox> outboxes = outboxReader.findConsumableOutboxes();
        if (outboxes.isEmpty()) return;

        outboxes.forEach(outbox -> {
            outbox.sent();
            // 외부 알림 호출
            // 비동기 처리, 다음 스케줄러가 돌기 전에 처리해야함
        });
    }
}
