CREATE TABLE reconciliation_alerts (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  date         DATE NOT NULL,
  diff_cents   BIGINT NOT NULL,
  payload_json JSON NOT NULL,
  status       ENUM('OPEN','ACKED','RESOLVED') NOT NULL DEFAULT 'OPEN',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at  DATETIME,
  UNIQUE KEY uk_date (date),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
