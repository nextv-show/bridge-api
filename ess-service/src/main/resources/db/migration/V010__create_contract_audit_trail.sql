-- V010 创建 contract_audit_trail 表
-- 合同全生命周期操作记录（创建/签署/归档/出证/撤销/查看/下载）

CREATE TABLE contract_audit_trail (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id     BIGINT         NOT NULL COMMENT '关联合同 ID',
    action          VARCHAR(64)    NOT NULL COMMENT '操作类型: CREATE/GENERATE/START_SIGN/SIGN_COMPLETE/ARCHIVE/CERTIFY/CERTIFY_FAIL/REVOKE/VIEW/DOWNLOAD',
    actor_id        VARCHAR(64)    NULL     COMMENT '操作者 ID',
    actor_type      VARCHAR(32)    NULL     COMMENT '操作者类型: USER/ADMIN/SYSTEM',
    metadata_json   TEXT           NULL     COMMENT '附加元数据 JSON',
    ip_address      VARCHAR(64)    NULL     COMMENT '操作者 IP',
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    INDEX idx_contract_id (contract_id),
    INDEX idx_action (action),
    INDEX idx_actor_id (actor_id),
    INDEX idx_created_at (created_at),
    INDEX idx_contract_action (contract_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同审计轨迹表';
