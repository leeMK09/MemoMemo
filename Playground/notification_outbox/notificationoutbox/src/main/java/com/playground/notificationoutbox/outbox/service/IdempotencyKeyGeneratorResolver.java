package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyGeneratorResolver {
    private final List<IdempotencyKeyGenerator> generators;

    public IdempotencyKeyGenerator resolve(IdempotencyKeyType keyType) {
        for (IdempotencyKeyGenerator generator : generators) {
            if (generator.support(keyType)) {
                return generator;
            }
        }
        log.error("지원하지 않는 keyType 입니다, keyType: {}", keyType);
        throw new IllegalArgumentException("지원하지 않는 keyType 입니다, keyType: " + keyType);
    }
}
