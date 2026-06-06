CREATE TABLE evidence_outbox (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id      BIGINT NOT NULL UNIQUE,
  payload_json JSON NOT NULL,
  next_run_at  DATETIME NOT NULL,
  retried      INT NOT NULL DEFAULT 0,
  status       ENUM('PENDING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
  KEY idx_pending (status, next_run_at)
);
