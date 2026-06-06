CREATE TABLE wallet_ledger (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT NOT NULL,
  direction           ENUM('IN','OUT') NOT NULL,
  source_type         ENUM('SETTLEMENT','WITHDRAWAL_FREEZE','WITHDRAWAL_REFUND','MANUAL_ADJUST') NOT NULL,
  source_id           BIGINT NOT NULL,
  amount_cents        BIGINT NOT NULL,
  balance_after_cents BIGINT NOT NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_source (source_type, source_id, direction),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
