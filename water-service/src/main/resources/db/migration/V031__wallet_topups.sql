CREATE TABLE wallet_topups (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  amount_cents   BIGINT NOT NULL,
  out_trade_no   VARCHAR(64) NOT NULL UNIQUE,
  wx_prepay_id   VARCHAR(64),
  wx_transaction_id VARCHAR(64) UNIQUE,
  status         ENUM('PENDING','PAID','FAILED','CLOSED') NOT NULL DEFAULT 'PENDING',
  paid_at        DATETIME,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id, status)
);
