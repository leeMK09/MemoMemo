package com.poc.notificationcdc.scheduler;

import com.poc.notificationcdc.config.CDCProperties;
import com.poc.notificationcdc.domain.OutboxEvent;
import com.poc.notificationcdc.infrastructure.cdc.CDCEvent;
import com.poc.notificationcdc.infrastructure.cdc.MySqlBinlogBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxConsumerScheduler {

    private final MySqlBinlogBuffer binlogBuffer;
    private final CDCProperties cdcProperties;
    private final OutboxRowMapper outboxRowMapper;

    @Scheduled(fixedDelayString = "${cdc.consumeFixedDelayMs:1000}")
    public void consume() {
        int size = Math.max(1, cdcProperties.getConsumeBatchSize());
        List<CDCEvent> batch = new ArrayList<>(size);
        binlogBuffer.drainTo(batch, size);

        if (batch.isEmpty()) {
            log.info("Nothing to consume");
            return;
        }

        for (CDCEvent event : batch) {
            List<Object> row = switch (event.operation()) {
                case INSERT, UPDATE -> event.after();
                case DELETE -> event.before();
            };

            OutboxEvent outbox = outboxRowMapper.map(row);
            log.info("[OUTBOX] " +
                            "id={} " +
                            "payload={} " +
                            "createdAt={}" +
                            "operation={}",
                    outbox.getId(),
                    outbox.getPayload(),
                    outbox.getCreatedAt(),
                    event.operation().name()
            );
        }
    }
}
