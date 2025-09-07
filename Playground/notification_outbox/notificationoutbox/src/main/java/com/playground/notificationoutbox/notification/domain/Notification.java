package com.playground.notificationoutbox.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Table(name = "notification")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id")
    private Long outboxId;

    public Notification(Long outboxId) {
        this.outboxId = outboxId;
    }
}
