-- V005 创建 contracts 表
-- 合同实例表，记录每份合同的生命周期

CREATE TABLE contracts (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_no           VARCHAR(32)    NOT NULL COMMENT '合同编号 CT-{yyyyMMdd}-{random6}',
    template_id           BIGINT         NOT NULL COMMENT '关联模板 ID',
    user_id               BIGINT         NOT NULL COMMENT '用户 ID',
    order_id              VARCHAR(64)    NULL     COMMENT '关联订单 ID',
    device_sn             VARCHAR(64)    NULL     COMMENT '设备 SN 码（预占位）',
    status                VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/GENERATED/SIGNING/SIGNED/ARCHIVED',
    signer_info_json      TEXT           NULL     COMMENT '签署方信息 JSON',
    contract_fields_json  TEXT           NULL     COMMENT '合同填充字段 JSON',
    pdf_url               VARCHAR(512)   NULL     COMMENT '合同 PDF 地址',
    pdf_hash              VARCHAR(128)   NULL     COMMENT '合同 PDF 哈希',
    ess_flow_id           VARCHAR(128)   NULL     COMMENT '腾讯电子签流程 ID',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_contract_no (contract_no),
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_device_sn (device_sn),
    INDEX idx_status (status),
    INDEX idx_template_id (template_id),
    INDEX idx_ess_flow_id (ess_flow_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同实例表';
