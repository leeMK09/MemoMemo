package com.playground.notificationoutbox.outbox.domain;

import java.util.List;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    FAILED,
    DEAD,
    COMPLETED;

    public static List<OutboxStatus> getConsumable() {
        return List.of(NEW, FAILED);
    }
}
