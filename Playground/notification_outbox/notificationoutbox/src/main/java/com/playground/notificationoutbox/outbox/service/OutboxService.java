package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.notification.service.dto.NotificationDispatchResult;
import com.playground.notificationoutbox.outbox.domain.*;
import com.playground.notificationoutbox.outbox.repository.OutboxJDBCRepository;
import com.playground.notificationoutbox.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final IdempotencyKeyGeneratorResolver resolver;
    private final OutboxRepository outboxRepository;
    private final OutboxJDBCRepository outboxJDBCRepository;

    @Transactional
    public void create(IdempotencySubject subject, Integer maxAttempts, Channel channel) {
        IdempotencyKeyType type = subject.getType();
        IdempotencyKeyGenerator generator = resolver.resolve(type);
        String idempotencyKey = generator.generate(subject);
        Outbox outbox = new Outbox(idempotencyKey, maxAttempts, channel);
        try {
            outboxRepository.save(outbox);
        } catch (DataIntegrityViolationException e) {
            log.error("유니크 제약 조건으로 인한 처리 실패 message : {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public List<Outbox> resolveConsumables(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxStatus> statuses = OutboxStatus.getConsumable();
        List<Outbox> consumableOutboxes = outboxRepository.findAllByStatusAndBeforeAttemptAtWithLock(
                statuses.stream().map(OutboxStatus::name).toList(),
                now,
                batchSize
        );

        if (consumableOutboxes.isEmpty()) {
            return List.of();
        }

        List<Long> outboxIds = consumableOutboxes.stream().map(Outbox::getId).toList();
        outboxRepository.updateAllStatusByIds(outboxIds, OutboxStatus.PROCESSING);
        return consumableOutboxes;
    }

    @Transactional
    public void failureAll(List<NotificationDispatchResult.Failure> failures) {
        try {
            outboxJDBCRepository.failure(failures);
        } catch (Exception e) {
            log.error("Failure 상태 변경 실패 error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void successAll(List<NotificationDispatchResult.Success> successes) {
        try {
            outboxJDBCRepository.success(successes);
        } catch (Exception e) {
            log.error("Completed 상태 변경 실패 error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
