CREATE TABLE payment_inbox (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id VARCHAR(64) UNIQUE NOT NULL,
  out_trade_no   VARCHAR(40),
  raw_body       TEXT,
  processed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
