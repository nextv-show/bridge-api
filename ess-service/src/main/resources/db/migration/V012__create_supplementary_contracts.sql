-- V012 创建 supplementary_contracts 表
-- 补充协议：冷静期后退款须签署《设备退货与服务终止补充协议》

CREATE TABLE supplementary_contracts (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_contract_id    BIGINT         NOT NULL COMMENT '原购机合同 ID',
    contract_no             VARCHAR(32)    NOT NULL COMMENT '补充协议编号',
    contract_type           VARCHAR(64)    NOT NULL DEFAULT 'DEVICE_RETURN' COMMENT '协议类型: DEVICE_RETURN/SERVICE_TERMINATION',
    status                  VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/GENERATED/SIGNING/SIGNED/ARCHIVED',
    pdf_url                 VARCHAR(512)   NULL     COMMENT 'PDF URL',
    pdf_hash                VARCHAR(128)   NULL     COMMENT 'PDF 哈希',
    ess_flow_id             VARCHAR(128)   NULL     COMMENT '腾讯电子签流程 ID',
    refund_order_id         VARCHAR(64)    NULL     COMMENT '关联退款订单 ID',
    signer_info_json        TEXT           NULL     COMMENT '签署方信息 JSON',
    contract_fields_json    TEXT           NULL     COMMENT '合同字段 JSON',
    signed_at               TIMESTAMP      NULL     COMMENT '签署完成时间',
    archived_at             TIMESTAMP      NULL     COMMENT '归档时间',
    created_at              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_original_contract_id (original_contract_id),
    INDEX idx_contract_no (contract_no),
    INDEX idx_status (status),
    INDEX idx_refund_order_id (refund_order_id),
    INDEX idx_ess_flow_id (ess_flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='补充协议表';
