CREATE TABLE h5_orders (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no          VARCHAR(40) UNIQUE NOT NULL,
  openid            VARCHAR(64) NOT NULL,
  spec_id           VARCHAR(32) NOT NULL,
  model_code        VARCHAR(32) NOT NULL,
  amount_cents      BIGINT NOT NULL,
  status            ENUM('PENDING_PAY','PAID','CLOSED','REFUNDED') NOT NULL,
  payment_channel   ENUM('WECHAT','ALIPAY') NOT NULL DEFAULT 'WECHAT',
  wx_prepay_id      VARCHAR(64),
  wx_transaction_id VARCHAR(64),
  sn                VARCHAR(64),
  cooldown_end_at   DATETIME,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at           DATETIME,
  closed_at         DATETIME,
  KEY idx_openid_status (openid, status),
  KEY idx_pending (status, created_at)
);
