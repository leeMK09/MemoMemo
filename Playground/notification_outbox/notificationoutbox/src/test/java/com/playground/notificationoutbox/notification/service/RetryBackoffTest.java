package com.playground.notificationoutbox.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RetryBackoffTest {

    @Test
    @DisplayName("다음 시도시간은 현재 시각 이상이어야 한다")
    void next_attempt_at_is_after() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = RetryBackoff.nextAttemptAt(0);

        assertFalse(next.isBefore(now));
    }
}
