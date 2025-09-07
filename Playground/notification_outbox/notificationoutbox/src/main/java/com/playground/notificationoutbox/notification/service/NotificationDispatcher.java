package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.infrastructure.NotificationSender;
import com.playground.notificationoutbox.notification.service.dto.NotificationDispatchResult;
import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {
    private final NotificationSender notificationSender;

    @Qualifier("notificationExecutor")
    private final Executor notificationExecutor;

    private final OutboxService outboxService;

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
                    outbox.sent();
                    notificationSender.send();
                    outbox.completed();
                    return NotificationDispatchResult.success(outbox.getId(), outbox.getStatus());
                } catch (Exception e) {
                    log.error("Failed to send notification", e);
                    outbox.failed();
                    return NotificationDispatchResult.failure(outbox.getId(), outbox.getStatus(), RetryBackoff.nextAttemptAt(outbox.getAttemptCount()), outbox.getAttemptCount());
                }
            }, notificationExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures;
    }

    public void applyResults(List<CompletableFuture<NotificationDispatchResult>> results) {
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

        outboxService.failureAll(failures);
        outboxService.successAll(successes);
    }
}
