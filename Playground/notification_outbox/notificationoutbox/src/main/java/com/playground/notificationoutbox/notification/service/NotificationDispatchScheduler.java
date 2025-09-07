package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchScheduler {
    private final OutboxService outboxService;
    private final NotificationDispatcher notificationDispatcher;

    @Value("${outbox.batch:50}")
    private int batchSize;

    @Scheduled(fixedRate = 5000)
    @SchedulerLock(
            name = "notification_dispatch_send",
            lockAtLeastFor = "PT1S",
            lockAtMostFor = "PT10S"
    )
    public void send() {
        log.info("Outbox 소비 시도 attempted at : {}", LocalDateTime.now());
        List<Outbox> outboxes = outboxService.resolveConsumables(batchSize);
        if (outboxes.isEmpty()) return;

        notificationDispatcher.dispatch(outboxes);
    }
}
