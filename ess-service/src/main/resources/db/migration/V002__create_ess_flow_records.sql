-- V002 创建 ess_flow_records 表
-- 签署流程记录表

CREATE TABLE ess_flow_records (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id           VARCHAR(64)    NOT NULL COMMENT '内部合同 ID',
    ess_flow_id           VARCHAR(128)   NULL     COMMENT '腾讯电子签 FlowId',
    flow_status           VARCHAR(32)    NOT NULL DEFAULT 'INIT' COMMENT '签署流程状态',
    signer_list_json      TEXT           NULL     COMMENT '签署人列表 JSON',
    callback_data_json    TEXT           NULL     COMMENT '回调数据 JSON',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uk_contract_id (contract_id),
    INDEX idx_ess_flow_id (ess_flow_id),
    INDEX idx_flow_status (flow_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='电子签签署流程记录';
