-- V008 创建 contract_access_logs 表
-- 合同访问审计日志（查看/下载/管理后台访问）

CREATE TABLE contract_access_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id     BIGINT         NOT NULL COMMENT '关联合同 ID',
    user_id         BIGINT         NULL     COMMENT '访问用户 ID',
    access_type     VARCHAR(32)    NOT NULL COMMENT '访问类型: VIEW/DOWNLOAD/ADMIN_VIEW/ADMIN_DOWNLOAD',
    access_source   VARCHAR(32)    NOT NULL COMMENT '访问来源: H5/ADMIN/API',
    ip_address      VARCHAR(64)    NULL     COMMENT '访问者 IP 地址',
    user_agent      VARCHAR(512)   NULL     COMMENT '访问者 User-Agent',
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',

    INDEX idx_contract_id (contract_id),
    INDEX idx_user_id (user_id),
    INDEX idx_access_type (access_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同访问审计日志';
