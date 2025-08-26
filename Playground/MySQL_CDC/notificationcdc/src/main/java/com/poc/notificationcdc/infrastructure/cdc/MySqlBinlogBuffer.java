package com.poc.notificationcdc.infrastructure.cdc;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.poc.notificationcdc.config.CDCProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class MySqlBinlogBuffer {

    private final CDCProperties properties;
    private BinaryLogClient client;

    private record TableMeta(String db, String table) {
        TableMeta {
            Objects.requireNonNull(db);
            Objects.requireNonNull(table);
        }
    }

    private final Map<Long, TableMeta> tableMeta = new ConcurrentHashMap<>();

    private final BlockingQueue<CDCEvent> buffer;

    public MySqlBinlogBuffer(CDCProperties properties) {
        this.properties = properties;

        int capacity = Math.max(1, properties.getQueueCapacity());
        this.buffer = new LinkedBlockingQueue<>(capacity);
    }

    public int drainTo(Collection<CDCEvent> events, int maxSize) {
        return buffer.drainTo(events, maxSize);
    }

    @PostConstruct
    public void init() {
        client = setReadPosition(initClient());

        client.setEventDeserializer(new EventDeserializer());

        client.registerEventListener(event -> {
            EventHeaderV4 header = event.getHeader();
            EventType eventType = header.getEventType();
            long occurredAt = header.getTimestamp();

            switch (eventType) {
                case TABLE_MAP -> bindTableMeta(event);
                case EXT_WRITE_ROWS, WRITE_ROWS -> onInsert(event, occurredAt);
                case EXT_UPDATE_ROWS, UPDATE_ROWS -> onUpdate(event, occurredAt);
                case EXT_DELETE_ROWS, DELETE_ROWS -> onDelete(event, occurredAt);
                default -> log.error("Unknown event type: {}", eventType);
            }
        });

        bindThread(client);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (client != null) client.disconnect();
        } catch (IOException ignored) {}
    }

    private BinaryLogClient initClient() {
        BinaryLogClient binaryLogClient = new BinaryLogClient(
                properties.getHost(),
                properties.getPort(),
                properties.getUsername(),
                properties.getPassword()
        );
        binaryLogClient.setServerId(properties.getServerId());
        binaryLogClient.setKeepAlive(true);
        binaryLogClient.setKeepAliveInterval(properties.getReconnectDelayMs());
        return binaryLogClient;
    }

    private BinaryLogClient setReadPosition(BinaryLogClient binaryLogClient) {
        if (!properties.isBlankGtidSet()) {
            binaryLogClient.setGtidSet(properties.getGtidSet());
            return binaryLogClient;
        }

        if (!properties.isBlankBinlogFileName() && !properties.isBlankBinlogPosition()) {
            binaryLogClient.setBinlogFilename(properties.getBinlogFileName());
            binaryLogClient.setBinlogPosition(properties.getBinlogPosition());
            return binaryLogClient;
        }

        return binaryLogClient;
    }

    private void bindThread(BinaryLogClient binaryLogClient) {
        Thread thread = new Thread(() -> {
            log.info("[CDC] connecting {}:{}", properties.getHost(), properties.getPort());
            try {
                binaryLogClient.connect(properties.getConnectTimeoutMs());
            } catch (IOException e) {
                log.error("[CDC] connect error", e);
            } catch (TimeoutException e) {
                log.error("[CDC] connect timeout error", e);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void bindTableMeta(Event dbEvent) {
        TableMapEventData tableMapEventData = dbEvent.getData();
        long tableId = tableMapEventData.getTableId();
        String database = tableMapEventData.getDatabase();
        String table = tableMapEventData.getTable();
        tableMeta.put(tableId, new TableMeta(database, table));
    }

    private void onInsert(Event dbEvent, long occurredAt) {
        WriteRowsEventData eventDate = dbEvent.getData();
        TableMeta eventTableMeta = tableMeta.get(eventDate.getTableId());
        if (!detect(eventTableMeta)) return;
        for (Serializable[] row : eventDate.getRows()) {
            enqueue(
                    new CDCEvent(
                            eventTableMeta.db,
                            eventTableMeta.table,
                            CDCEvent.Operation.INSERT,
                            occurredAt,
                            null,
                            Arrays.asList(row)
                    )
            );
        }
    }

    private void onUpdate(Event dbEvent, long occurredAt) {
        UpdateRowsEventData eventDate = dbEvent.getData();
        TableMeta eventTableMeta = tableMeta.get(eventDate.getTableId());
        if (!detect(eventTableMeta)) return;
        for (Map.Entry<Serializable[], Serializable[]> eventMap : eventDate.getRows()) {
            Serializable[] before = eventMap.getKey();
            Serializable[] after  = eventMap.getValue();

            enqueue(new CDCEvent(
                    eventTableMeta.db(),
                    eventTableMeta.table(),
                    CDCEvent.Operation.UPDATE,
                    occurredAt,
                    before == null ? null : Arrays.asList(before),
                    after  == null ? null : Arrays.asList(after)
            ));
        }
    }

    private void onDelete(Event dbEvent, long occurredAt) {
        DeleteRowsEventData eventDate = dbEvent.getData();
        TableMeta eventTableMeta = tableMeta.get(eventDate.getTableId());
        if (!detect(eventTableMeta)) return;
        for (Serializable[] row : eventDate.getRows()) {
            enqueue(
                    new CDCEvent(
                            eventTableMeta.db,
                            eventTableMeta.table,
                            CDCEvent.Operation.DELETE,
                            occurredAt,
                            Arrays.asList(row),
                            null
                    )
            );
        }
    }

    private void enqueue(CDCEvent event) {
        boolean isSuccess = buffer.offer(event);

        if (!isSuccess) {
            log.error("[DROP] Binlog event queue is full");
        }
    }

    private boolean detect(TableMeta eventTableMeta) {
        if (eventTableMeta == null) return false;
        String schema = properties.getSchema();
        if (schema == null) return false;
        if (!schema.equalsIgnoreCase(eventTableMeta.db)) return false;

        String includeTable = properties.getIncludeTable();
        if (includeTable == null) return false;
        return includeTable.equalsIgnoreCase(eventTableMeta.table);
    }
}
