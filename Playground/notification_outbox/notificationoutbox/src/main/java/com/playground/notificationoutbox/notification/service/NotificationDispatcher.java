package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.infrastructure.NotificationSender;
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

        List<CompletableFuture<Result>> futures = asyncNotificationSender(outboxes);
        applyResults(futures);
    }

    private List<CompletableFuture<Result>> asyncNotificationSender(List<Outbox> outboxes) {
        List<CompletableFuture<Result>> futures = new ArrayList<>(outboxes.size());
        for (Outbox outbox : outboxes) {
            CompletableFuture<Result> future = CompletableFuture.supplyAsync(() -> {
                try {
                    notificationSender.send();
                    return Result.success(outbox.getId());
                } catch (Exception e) {
                    log.error("Failed to send notification", e);
                    return Result.failure(outbox.getId());
                }
            }, notificationExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures;
    }

    private void applyResults(List<CompletableFuture<Result>> results) {
        List<Long> successIds = new ArrayList<>();
        List<Long> failureIds = new ArrayList<>();

        for (CompletableFuture<Result> future : results) {
            Result result = future.join();

            if (result.isSuccess) {
                successIds.add(result.outboxId);
            } else {
                failureIds.add(result.outboxId);
            }
        }
    }

    private static class Result {
        boolean isSuccess;
        Long outboxId;

        public Result(boolean isSuccess, Long outboxId) {
            this.isSuccess = isSuccess;
            this.outboxId = outboxId;
        }

        public static Result success(Long outboxId) {
            return new Result(true, outboxId);
        }

        public static Result failure(Long outboxId) {
            return new Result(false, outboxId);
        }
    }
}
