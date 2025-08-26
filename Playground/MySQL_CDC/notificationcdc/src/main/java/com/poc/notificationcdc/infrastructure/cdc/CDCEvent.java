package com.poc.notificationcdc.infrastructure.cdc;

import java.util.List;

public record CDCEvent(
        String database,
        String table,
        Operation operation,
        long commitTs,
        List<Object> before,
        List<Object> after
) {
    public enum Operation {
        INSERT,
        UPDATE,
        DELETE
    }
}
