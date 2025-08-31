package com.playground.notificationoutbox.outbox.repository;

import com.playground.notificationoutbox.outbox.domain.Outbox;
import com.playground.notificationoutbox.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    @Query(
            value = """
                SELECT * FROM outbox
                WHERE status IN (:statuses)
                AND next_attempt_at <= :now
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<Outbox> findAllByStatusAndBeforeAttemptAtWithLock(
            @Param("statuses") List<OutboxStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
        UPDATE Outbox o
        SET o.status = :status
        WHERE o.id IN :ids
    """)
    int updateAllStatusByIds(@Param("ids") List<Long> ids, OutboxStatus status);
}
