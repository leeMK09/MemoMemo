package com.playground.notificationoutbox.notification.service;

import jakarta.annotation.Resource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatchSchedulerTest {

    @Resource
    private LockProvider lockProvider;

    @Test
    @DisplayName("스케줄러 실행시 동일 락 이름으로 동시 실행하면 하나의 스케줄러만 수행된다")
    void duplicatedLock() throws InterruptedException {
        DefaultLockingTaskExecutor exec = new DefaultLockingTaskExecutor(lockProvider);
        AtomicInteger executionCounter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);

        Runnable task = () -> {
            String duplicatedLockName = "test-lock";
            LockConfiguration lockConfig = new LockConfiguration(
                    Instant.now(),
                    duplicatedLockName,
                    Duration.ofSeconds(5),
                    Duration.ofMillis(200)
            );
            exec.executeWithLock((Runnable) () -> {
                LockAssert.assertLocked();
                executionCounter.incrementAndGet();
            }, lockConfig);
            latch.countDown();
        };

        new Thread(task).start();
        new Thread(task).start();
        latch.await();

        assertEquals(1, executionCounter.get());
    }
}
