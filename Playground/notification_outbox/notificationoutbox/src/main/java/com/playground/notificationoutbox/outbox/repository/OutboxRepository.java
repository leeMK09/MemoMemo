package com.playground.notificationoutbox.outbox.repository;

import com.playground.notificationoutbox.outbox.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
}
