package com.playground.notificationoutbox.outbox.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxTest {
    @Test
    @DisplayName("Outbox 를 전송할 경우 시도횟수가 1 증가한다")
    void failed_when_inc_attempt_cnt() {
        Integer maxReached = 3;
        Outbox outbox = new Outbox("", maxReached);

        outbox.sent();
        Integer attemptCount1 = outbox.getAttemptCount();

        assertEquals(1, attemptCount1);

        outbox.sent();
        Integer attemptCount2 = outbox.getAttemptCount();

        assertEquals(2, attemptCount2);
    }

    @Test
    @DisplayName("최대 시도횟수를 넘어갔다면 true 를 반환한다")
    void isMaxReached() {
        Integer maxReached = 3;
        Outbox outbox = new Outbox("", maxReached);

        outbox.sent();
        outbox.sent();
        outbox.sent();
        boolean result = outbox.isMaxReached();

        assertTrue(result);
    }

    @Test
    @DisplayName("요청 실패 후 최대 시도횟수를 넘어갔다면 'DEAD' 상태가 된다")
    void dead() {
        Integer maxReached = 3;
        Outbox outbox = new Outbox("", maxReached);

        outbox.sent();
        outbox.sent();
        outbox.sent();
        outbox.failed();
        OutboxStatus status = outbox.getStatus();

        assertEquals(OutboxStatus.DEAD, status);
    }
}
