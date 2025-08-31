package com.playground.notificationoutbox.outbox.domain;

import java.util.List;

public enum OutboxStatus {
    NEW,
    SENT,
    FAILED,
    DEAD,
    COMPLETED;

    public static List<OutboxStatus> getConsumableStatuses() {
        return List.of(NEW, FAILED);
    }
}
