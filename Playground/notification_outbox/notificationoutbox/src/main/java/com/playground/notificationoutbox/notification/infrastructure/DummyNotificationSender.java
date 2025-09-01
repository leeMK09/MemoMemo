package com.playground.notificationoutbox.notification.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(prefix = "notification.http", name = "dummy", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class DummyNotificationSender implements NotificationSender {
    private final WebClient notificationWebClient;

    @Override
    public void send() {
        log.info("알림 요청");
        notificationWebClient.post()
                .uri("/post")
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
