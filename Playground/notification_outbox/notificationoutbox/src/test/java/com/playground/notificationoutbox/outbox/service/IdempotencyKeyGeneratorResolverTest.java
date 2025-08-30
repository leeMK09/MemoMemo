package com.playground.notificationoutbox.outbox.service;

import com.playground.notificationoutbox.notification.service.NotificationKeyGenerator;
import com.playground.notificationoutbox.outbox.domain.IdempotencyKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeyGeneratorResolverTest {
    private IdempotencyKeyGeneratorResolver resolver = new IdempotencyKeyGeneratorResolver(
            List.of(new NotificationKeyGenerator())
    );

    @Test
    @DisplayName("지원가능한 타입이라면 적절한 멱등키 생성기를 반환한다")
    void resolve_supported() {
        IdempotencyKeyType supportedType = IdempotencyKeyType.NOTIFICATION;

        IdempotencyKeyGenerator generator = resolver.resolve(supportedType);

        assertNotNull(generator);
        assertInstanceOf(NotificationKeyGenerator.class, generator);
    }
}
