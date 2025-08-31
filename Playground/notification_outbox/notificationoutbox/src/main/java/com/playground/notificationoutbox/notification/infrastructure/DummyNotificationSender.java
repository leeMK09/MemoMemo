package com.playground.notificationoutbox.notification.infrastructure;

import com.playground.notificationoutbox.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(prefix = "notification.http", name = "dummy", havingValue = "true")
@RequiredArgsConstructor
public class DummyNotificationSender implements NotificationSender {
    private final WebClient notificationWebClient;

    @Override
    public void send() {
        notificationWebClient.post()
                .uri("/post")
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
