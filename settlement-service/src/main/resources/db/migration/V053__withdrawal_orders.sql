CREATE TABLE withdrawal_orders (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  gross_cents     BIGINT NOT NULL,
  fee_cents       BIGINT NOT NULL,
  cash_cents      BIGINT NOT NULL,
  status          ENUM('PENDING','PROCESSING','PARTIAL_DONE','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
  failure_reason  VARCHAR(255),
  client_request_id VARCHAR(64),
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at    DATETIME,
  UNIQUE KEY uk_client_req (user_id, client_request_id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_status (status),
  CONSTRAINT chk_sum CHECK (fee_cents + cash_cents = gross_cents),
  CONSTRAINT chk_amounts_nonneg CHECK (gross_cents >= 0 AND fee_cents >= 0 AND cash_cents >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
