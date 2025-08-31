package com.playground.notificationoutbox.outbox.domain;

public enum OutboxStatus {
    NEW,
    SENT,
    FAILED,
    DEAD,
    COMPLETED
}
