-- V013 创建 refund_contract_linkages 表
-- 退款与补充协议联动表：冷静期/补充协议/退款三方状态联动

CREATE TABLE refund_contract_linkages (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplementary_contract_id   BIGINT         NOT NULL COMMENT '补充协议 ID',
    refund_order_id             VARCHAR(64)    NOT NULL COMMENT '退款订单 ID',
    linkage_status              VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT '联动状态: PENDING/SUPPLEMENTARY_SIGNED/REFUND_APPROVED/REFUND_COMPLETED/CANCELLED',
    created_at                  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_supplementary_contract_id (supplementary_contract_id),
    INDEX idx_refund_order_id (refund_order_id),
    INDEX idx_linkage_status (linkage_status),
    UNIQUE INDEX uk_refund_order_id (refund_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='退款合同联动表';
