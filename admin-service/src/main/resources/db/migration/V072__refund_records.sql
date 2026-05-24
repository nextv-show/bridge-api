CREATE TABLE IF NOT EXISTS refund_records (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id              BIGINT NOT NULL,
  user_id               BIGINT NOT NULL,
  refund_no             VARCHAR(64) NOT NULL UNIQUE,
  refund_type           VARCHAR(16) NOT NULL,
  status                VARCHAR(16) NOT NULL,
  reason_cat            VARCHAR(32) NOT NULL,
  user_msg              TEXT,
  reject_reason         VARCHAR(256),

  -- 金额（全部 cents）
  order_amount_cents    BIGINT NOT NULL,
  paid_amount_cents     BIGINT NOT NULL,
  refund_amount_cents   BIGINT NOT NULL,
  actual_refund_cents   BIGINT,
  income_deducted_cents BIGINT NOT NULL DEFAULT 0,
  fee_cents             BIGINT NOT NULL DEFAULT 0,

  -- 支付信息
  payment_channel       VARCHAR(16),
  payment_txn_id        VARCHAR(64),

  -- 设备信息（冗余快照）
  device_sn             VARCHAR(64),
  device_model          VARCHAR(128),
  device_stage          VARCHAR(32),
  install_addr          VARCHAR(256),

  -- SKU 信息
  sku_name              VARCHAR(128),
  sku_qty               INT NOT NULL DEFAULT 1,

  -- 风险等级
  risk_level            VARCHAR(16) NOT NULL DEFAULT 'low',

  -- 脱敏用户信息
  real_name_mask        VARCHAR(32),
  phone_mask            VARCHAR(32),
  kyc_passed            BOOLEAN NOT NULL DEFAULT FALSE,

  -- 时间
  submitted_at          DATETIME NOT NULL,
  resolved_at           DATETIME,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  -- 审批人
  operator_id           BIGINT,
  operator_name         VARCHAR(64),

  KEY idx_order_id (order_id),
  KEY idx_user_id (user_id),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
);
