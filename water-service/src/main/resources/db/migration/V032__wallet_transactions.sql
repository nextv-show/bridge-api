CREATE TABLE wallet_transactions (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  direction          ENUM('IN','OUT') NOT NULL,
  source_type        ENUM('TOPUP','WATER_BILL','REFUND') NOT NULL,
  source_id          BIGINT NOT NULL,
  amount_cents       BIGINT NOT NULL,
  balance_after_cents BIGINT NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source (source_type, source_id, direction),
  KEY idx_user_time (user_id, created_at)
);
