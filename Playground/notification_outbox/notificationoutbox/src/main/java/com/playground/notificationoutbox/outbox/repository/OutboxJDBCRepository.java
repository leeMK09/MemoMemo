package com.playground.notificationoutbox.outbox.repository;

import com.playground.notificationoutbox.notification.service.dto.NotificationDispatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public void failure(List<NotificationDispatchResult.Failure> failures) {
        jdbcTemplate.batchUpdate("""
            UPDATE outbox
            SET attempt_count = ?
            status = ?,
            next_attempt_at = ?
            WHERE id = ?
        """, failures, failures.size(), (ps, r) -> {
            ps.setInt(1, r.attemptCount());
            ps.setString(2, r.status().name());
            ps.setObject(3, r.nextAttemptAt());
            ps.setLong(4, r.outboxId());
        });
    }

    public void success(List<NotificationDispatchResult.Success> successes) {
        jdbcTemplate.batchUpdate("""
            UPDATE outbox
            status = ?,
            WHERE id = ?
        """, successes, successes.size(), (ps, r) -> {
            ps.setString(1, r.status().name());
            ps.setLong(2, r.outboxId());
        });
    }
}
