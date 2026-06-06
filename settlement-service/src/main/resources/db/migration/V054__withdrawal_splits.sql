CREATE TABLE withdrawal_splits (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id     BIGINT NOT NULL,
  kind         ENUM('CASH') NOT NULL,
  amount_cents BIGINT NOT NULL,
  channel      ENUM('WX_MCH_PAYOUT') NOT NULL,
  external_id  VARCHAR(128),
  status       ENUM('QUEUED','PAYING','PAID','FAILED') NOT NULL DEFAULT 'QUEUED',
  failure_reason VARCHAR(255),
  paid_at      DATETIME,
  retried      INT NOT NULL DEFAULT 0,
  next_run_at  DATETIME,
  UNIQUE KEY uk_order_kind (order_id, kind),
  KEY idx_pending (status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
