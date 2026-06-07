CREATE TABLE alarms_outbox (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  alarm_id     BIGINT NOT NULL UNIQUE,
  payload_json JSON NOT NULL,
  consumed_at  DATETIME(3) NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_pending (consumed_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
