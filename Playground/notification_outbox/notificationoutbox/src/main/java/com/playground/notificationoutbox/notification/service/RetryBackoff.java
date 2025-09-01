package com.playground.notificationoutbox.notification.service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryBackoff {
    // 기본 딜레이 시간(초)
    private static final long baseSec = 5;

    // 최대 딜레이 시간(초)
    private static final long capacitySec = 60;

    // 증가폭(지수 계산)
    private static final int factor = 2;

    // 최대 횟수
    private static final int maxAttempts = 5;

    public static LocalDateTime nextAttemptAt(int attemptCount) {
        long backoff = expectedCapacity(attemptCount);
        long delay = ThreadLocalRandom.current().nextLong(backoff + 1);
        return LocalDateTime.now().plusSeconds(delay);
    }

    private static long expectedCapacity(int attemptCount) {
        int attempt = Math.min(attemptCount, maxAttempts);
        double capacity = baseSec * Math.pow(factor, attempt);
        return Math.min((long) capacity, capacitySec);
    }
}
