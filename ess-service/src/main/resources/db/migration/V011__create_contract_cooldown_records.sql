-- V011 创建 contract_cooldown_records 表
-- 冷静期记录：24h 冷静期从支付成功开始计算

CREATE TABLE contract_cooldown_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id         BIGINT         NOT NULL COMMENT '关联合同 ID',
    order_id            VARCHAR(64)    NOT NULL COMMENT '关联订单 ID',
    user_id             BIGINT         NOT NULL COMMENT '用户 ID',
    cooldown_start_at   TIMESTAMP      NOT NULL COMMENT '冷静期开始时间（支付成功时间）',
    cooldown_end_at     TIMESTAMP      NOT NULL COMMENT '冷静期结束时间（开始+24h）',
    status              VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRED/REVOKED/CANCELLED',
    revoked_at          TIMESTAMP      NULL     COMMENT '撤销时间',
    revoke_reason       VARCHAR(512)   NULL     COMMENT '撤销原因',
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_contract_id (contract_id),
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_cooldown_end_at (cooldown_end_at),
    UNIQUE INDEX uk_contract_id (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='冷静期记录表';
