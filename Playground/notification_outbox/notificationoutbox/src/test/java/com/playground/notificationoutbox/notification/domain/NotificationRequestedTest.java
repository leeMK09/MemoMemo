package com.playground.notificationoutbox.notification.domain;

import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class NotificationRequestedTest {
    @Test
    @DisplayName("getType() 실행 시 NOTIFICATION 타입을 반환한다")
    void getType() {
        NotificationRequested notificationRequested = new NotificationRequested(Instant.now());

        IdempotencyKeyType result = notificationRequested.getType();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(IdempotencyKeyType.NOTIFICATION);
    }

    @Test
    @DisplayName("getOccurredAt() 실행 시 발생한 시간을 반환한다")
    void getOccurredAt() {
        Instant startedAt = Instant.now();
        NotificationRequested notificationRequested = new NotificationRequested(startedAt);

        Instant occurredAt = notificationRequested.occurredAt();

        assertThat(occurredAt).isNotNull();
        assertThat(occurredAt).isEqualTo(startedAt);
    }
}
