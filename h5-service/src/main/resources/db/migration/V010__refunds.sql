CREATE TABLE refunds (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT NOT NULL,
  refund_no     VARCHAR(64) NOT NULL,
  wx_refund_id  VARCHAR(64),
  amount_cents  BIGINT NOT NULL,
  status        ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING',
  reason        VARCHAR(128) NOT NULL DEFAULT '冷静期无理由退款',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  succeeded_at  DATETIME,
  UNIQUE KEY uk_order (order_id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_status (status)
);
