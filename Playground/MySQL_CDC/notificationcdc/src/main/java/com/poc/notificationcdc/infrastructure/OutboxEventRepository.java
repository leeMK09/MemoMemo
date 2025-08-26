package com.poc.notificationcdc.infrastructure;

import com.poc.notificationcdc.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
