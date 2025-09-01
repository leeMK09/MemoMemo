package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.infrastructure.NotificationSender;
import com.playground.notificationoutbox.notification.service.dto.NotificationDispatchResult;
import com.playground.notificationoutbox.outbox.domain.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {
    private final NotificationSender notificationSender;

    @Qualifier("notificationExecutor")
    private final Executor notificationExecutor;

    public void dispatch(List<Outbox> outboxes) {
        if (outboxes.isEmpty()) return;

        List<CompletableFuture<NotificationDispatchResult>> futures = asyncNotificationSender(outboxes);
        applyResults(futures);
    }

    private List<CompletableFuture<NotificationDispatchResult>> asyncNotificationSender(List<Outbox> outboxes) {
        List<CompletableFuture<NotificationDispatchResult>> futures = new ArrayList<>(outboxes.size());
        for (Outbox outbox : outboxes) {
            CompletableFuture<NotificationDispatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    notificationSender.send();
                    return NotificationDispatchResult.success(outbox.getId());
                } catch (Exception e) {
                    log.error("Failed to send notification", e);
                    return NotificationDispatchResult.failure(outbox.getId(), RetryBackoff.nextAttemptAt(outbox.getAttemptCount()));
                }
            }, notificationExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures;
    }

    private void applyResults(List<CompletableFuture<NotificationDispatchResult>> results) {
        List<NotificationDispatchResult.Success> successes = new ArrayList<>();
        List<NotificationDispatchResult.Failure> failures = new ArrayList<>();

        for (CompletableFuture<NotificationDispatchResult> future : results) {
            NotificationDispatchResult result = future.join();

            if (NotificationDispatchResult.isSuccess(result)) {
                successes.add((NotificationDispatchResult.Success) result);
            } else {
                failures.add((NotificationDispatchResult.Failure) result);
            }
        }

        // 벌크 업데이트 [성공/실패]
    }
}
