CREATE TABLE settlement_outbox (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  aggregate_type  VARCHAR(32) NOT NULL,
  aggregate_id    VARCHAR(64) NOT NULL,
  event_type      ENUM('STAGE_CHANGED','ENTRY_POSTED','RECONCILE_FAILED') NOT NULL,
  payload_json    JSON NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  status          ENUM('PENDING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  retried         INT NOT NULL DEFAULT 0,
  next_run_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at    DATETIME,
  UNIQUE KEY uk_idempotency (idempotency_key),
  KEY idx_pending (status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
