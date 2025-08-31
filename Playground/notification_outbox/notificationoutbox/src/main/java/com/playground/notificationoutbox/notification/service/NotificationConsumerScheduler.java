package com.playground.notificationoutbox.notification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumerScheduler {

    @Scheduled(fixedRate = 5000)
    public void send() {

    }
}
