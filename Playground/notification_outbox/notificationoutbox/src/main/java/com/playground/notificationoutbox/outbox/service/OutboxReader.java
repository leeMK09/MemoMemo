package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.outbox.domain.Outbox;

import java.util.List;

public interface OutboxReader {
    List<Outbox> findConsumableOutboxes();
}
