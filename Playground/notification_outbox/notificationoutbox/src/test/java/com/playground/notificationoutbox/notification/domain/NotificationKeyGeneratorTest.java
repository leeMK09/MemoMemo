package com.playground.notificationoutbox.notification.domain;

import com.playground.notificationoutbox.notification.service.NotificationKeyGenerator;
import com.playground.notificationoutbox.outbox.domain.Channel;
import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    @DisplayName("멱등키는 'employer전화번호_worker전화번호_YYYY-MM-DD_CHANNEL' 형태로 만들어진다")
    void generate() {
        String employerPhoneNumber = "123456789";
        String workerPhoneNumber = "987654321";
        LocalDateTime occurredAt = LocalDateTime.now();
        Channel sms = Channel.SMS;
        NotificationRequested notificationRequested = new NotificationRequested(occurredAt, employerPhoneNumber, workerPhoneNumber, sms);

        NotificationKeyGenerator generator = new NotificationKeyGenerator();
        String idempotencyKey = generator.generate(notificationRequested);
        String yyyymmdd = occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().toString();
        String expected = employerPhoneNumber + "_" + workerPhoneNumber + "_" + yyyymmdd + "_" + sms;

        assertNotNull(idempotencyKey);
        assertEquals(expected, idempotencyKey);
    }
}
