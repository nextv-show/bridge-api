-- 消费者水费钱包（预收账款余额 + 健康积分 + 水量配额）。一个用户一行。
CREATE TABLE consumer_wallet (
  user_id              BIGINT PRIMARY KEY,
  balance_cents        BIGINT NOT NULL DEFAULT 0,
  points               INT    NOT NULL DEFAULT 0,
  liters_quota         INT    NOT NULL DEFAULT 0,
  daily_avg_cents      BIGINT NOT NULL DEFAULT 0,
  last_recharge_cents  BIGINT NULL,
  last_recharge_at     DATETIME NULL,
  created_at           DATETIME NOT NULL,
  updated_at           DATETIME NOT NULL
);

-- 充值单（预收账款）。仅用于水费消费，不计利息、不可提现。
CREATE TABLE wallet_recharge (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  amount_cents       BIGINT NOT NULL,
  points_granted     INT    NOT NULL DEFAULT 0,
  liters_granted     INT    NOT NULL DEFAULT 0,
  status             ENUM('PENDING_PAY','PAID','CANCELLED') NOT NULL,
  pay_channel        VARCHAR(24) NULL,
  wx_transaction_id  VARCHAR(64) NULL,
  created_at         DATETIME NOT NULL,
  paid_at            DATETIME NULL,
  KEY idx_recharge_user (user_id)
);
