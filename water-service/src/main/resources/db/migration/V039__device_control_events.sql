-- Mock control-plane outbox table for lock/unlock events.
-- In production this would live in a separate control-plane database;
-- for V1 we colocate in water_db and treat water-service as the consumer.
CREATE TABLE IF NOT EXISTS device_control_events (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn                VARCHAR(64) NOT NULL,
    event_type        VARCHAR(32) NOT NULL,
    can_dispense      BOOLEAN NOT NULL,
    reason            VARCHAR(64),
    consumed_by_water DATETIME,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_unconsumed (event_type, consumed_by_water)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
