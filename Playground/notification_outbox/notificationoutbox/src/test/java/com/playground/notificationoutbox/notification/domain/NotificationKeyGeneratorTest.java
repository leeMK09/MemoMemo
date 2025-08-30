package com.playground.notificationoutbox.notification.domain;

import com.playground.notificationoutbox.notification.service.NotificationKeyGenerator;
import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NotificationKeyGeneratorTest {
    @Test
    @DisplayName("NOTIFICATION 타입인 경우 true 를 반환한다")
    void support_true() {
        NotificationKeyGenerator generator = new NotificationKeyGenerator();

        boolean isSupport = generator.support(IdempotencyKeyType.NOTIFICATION);

        assertTrue(isSupport);
    }

    @Test
    @DisplayName("멱등키는 'employer 전화번호_worker 전화번호_발생시간' 형태로 만들어진다")
    void generate() {
        String employerPhoneNumber = "123456789";
        String workerPhoneNumber = "987654321";
        Instant occurredAt = Instant.now();
        NotificationRequested notificationRequested = new NotificationRequested(occurredAt, employerPhoneNumber, workerPhoneNumber);

        NotificationKeyGenerator generator = new NotificationKeyGenerator();
        String idempotencyKey = generator.generate(notificationRequested);
        String expected = employerPhoneNumber + "_" + workerPhoneNumber + "_" + occurredAt.toString();

        assertNotNull(idempotencyKey);
        assertEquals(expected, idempotencyKey);
    }
}
