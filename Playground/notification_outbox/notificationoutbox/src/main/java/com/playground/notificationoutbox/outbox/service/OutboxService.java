package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import com.playground.notificationoutbox.outbox.domain.IdempotencySubject;
import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final IdempotencyKeyGeneratorResolver resolver;
    private final OutboxRepository outboxRepository;

    @Transactional
    public void create(IdempotencySubject subject) {
        IdempotencyKeyType type = subject.getType();
        IdempotencyKeyGenerator generator = resolver.resolve(type);
        String idempotencyKey = generator.generate(subject);
        Outbox outbox = new Outbox(idempotencyKey);
        try {
            outboxRepository.save(outbox);
        } catch (DataIntegrityViolationException e) {
            log.error("유니크 제약 조건으로 인한 처리 실패 message : {}", e.getMessage(), e);
            throw e;
        }
    }
}
