package com.poc.notificationcdc.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.shyiko.mysql.binlog.event.deserialization.json.JsonBinary;
import com.poc.notificationcdc.domain.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public final class OutboxRowMapper {

    private final int idxId;
    private final int idxPayload;
    private final int idxCreatedAt;

    public OutboxRowMapper(List<String> columns) {
        this.idxId = indexOf(columns, "id");
        this.idxPayload = indexOf(columns, "payload");
        this.idxCreatedAt = indexOf(columns, "created_at");
    }

    private static int indexOf(List<String> cols, String name) {
        for (int i = 0; i < cols.size(); i++) {
            if (name.equalsIgnoreCase(cols.get(i))) return i;
        }
        throw new IllegalArgumentException("Column not found: " + name);
    }

    public OutboxEvent map(List<Object> row) {
        return map(row == null ? null : row.toArray());
    }

    public OutboxEvent map(Object[] row) {
        if (row == null) return null;

        Long id = row[idxId] == null ? null : ((Number) row[idxId]).longValue();

        String payload = null;
        Object pv = row[idxPayload];
        if (pv instanceof String s) payload = s;
        else if (pv instanceof byte[] b) {
            try {
                payload = JsonBinary.parseAsString(b);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else if (pv != null) payload = String.valueOf(pv);

        LocalDateTime createdAt = null;
        Object ov = row[idxCreatedAt];
        if (ov instanceof Timestamp ts) createdAt = ts.toLocalDateTime();
        else if (ov instanceof LocalDateTime ldt) createdAt = ldt;
        else if (ov instanceof String s) createdAt = LocalDateTime.parse(s.replace(" ", "T"));

        return OutboxEvent.builder()
                .id(id)
                .payload(payload)
                .createdAt(createdAt)
                .build();
    }

    // 헬퍼: 한번만 호출해서 컬럼목록 만들어 전달
    public static OutboxRowMapper load(JdbcTemplate jdbc, String schema, String table) {
        String sql = """
          SELECT COLUMN_NAME
          FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
          ORDER BY ORDINAL_POSITION
        """;
        List<String> cols = jdbc.query(sql, (rs, i) -> rs.getString(1), schema, table);
        return new OutboxRowMapper(cols);
    }
}
