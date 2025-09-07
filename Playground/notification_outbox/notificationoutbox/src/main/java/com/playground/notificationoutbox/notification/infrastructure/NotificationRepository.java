package com.playground.notificationoutbox.notification.infrastructure;

import com.playground.notificationoutbox.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
