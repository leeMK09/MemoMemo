package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.notification.domain.NotificationRequested;
import com.playground.notificationoutbox.notification.service.dto.NotificationCreation;
import com.playground.notificationoutbox.outbox.domain.IdempotencySubject;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    OutboxService outboxService;

    @InjectMocks
    NotificationService notificationService;

    @Test
    @DisplayName("알림 생성시 OutboxService.create 가 호출된다")
    void create() {
        NotificationCreation creation = createNotificationCreation();

        notificationService.create(creation);

        verify(outboxService, times(1)).create(any(IdempotencySubject.class), 5);
    }

    @Test
    @DisplayName("알림 생성시 OutboxService.create 에 NotificationRequested 이벤트가 전달된다")
    void create_passed_notification_requested() {
        NotificationCreation creation = createNotificationCreation();

        notificationService.create(creation);

        ArgumentCaptor<IdempotencySubject> captor = ArgumentCaptor.forClass(IdempotencySubject.class);
        verify(outboxService, times(1)).create(captor.capture(), 5);

        IdempotencySubject subject = captor.getValue();
        assertEquals(NotificationRequested.class, subject.getClass());
    }

    private NotificationCreation createNotificationCreation() {
        return new NotificationCreation(1L, 1L);
    }
}
