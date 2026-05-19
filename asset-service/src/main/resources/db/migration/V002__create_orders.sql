CREATE TABLE orders (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT NOT NULL,
  sku_id            BIGINT NOT NULL,
  qty               INT NOT NULL,
  amount_cents      BIGINT NOT NULL,
  status            ENUM('PENDING_PAY','PAID','CLOSED','REFUND') NOT NULL,
  wx_prepay_id      VARCHAR(64),
  wx_transaction_id VARCHAR(64),
  address_snapshot  JSON NOT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at           DATETIME,
  closed_at         DATETIME,
  KEY idx_user (user_id, status),
  KEY idx_sku (sku_id)
);
