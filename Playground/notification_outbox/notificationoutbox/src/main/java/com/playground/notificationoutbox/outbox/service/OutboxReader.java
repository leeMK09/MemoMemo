package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.outbox.service.dto.OutboxResult;

import java.util.List;

public interface OutboxReader {
    List<OutboxResult> findConsumableOutboxes();
}
