package com.sanshuiyuan.logistics.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 读取 matching-service 写入的 logistics_outbox 表（同 core_db 内，通过 JdbcTemplate 直读）。
 * logistics-service 不拥有 outbox JPA 实体；用 JDBC 跨表读取。
 */
@Component
public class OutboxReader {

    private static final String PENDING_SQL =
            "SELECT id, request_id, device_asset_id, payload_json FROM logistics_outbox " +
            "WHERE consumed_at IS NULL ORDER BY created_at ASC LIMIT 50";

    private static final String MARK_CONSUMED_SQL =
            "UPDATE logistics_outbox SET consumed_at = NOW() WHERE id = ?";

    private final JdbcTemplate jdbc;

    public OutboxReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> fetchPending() {
        return jdbc.queryForList(PENDING_SQL);
    }

    public void markConsumed(long outboxId) {
        jdbc.update(MARK_CONSUMED_SQL, outboxId);
    }
}
