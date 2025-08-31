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
public class NotificationConsumerScheduler {
    private final OutboxService outboxService;

    @Value("${outbox.batch:50}")
    private int batchSize;

    @Scheduled(fixedRate = 5000)
    public void send() {
        List<Outbox> outboxes = outboxService.resolveConsumables(batchSize);
        if (outboxes.isEmpty()) return;

        outboxes.forEach(outbox -> {
            // 외부 알림 호출
            // 비동기 처리, 다음 스케줄러가 돌기 전에 처리해야함
        });
    }
}
