-- V001 创建 ess_api_logs 表
-- 腾讯电子签 API 调用审计日志

CREATE TABLE ess_api_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_action      VARCHAR(64)   NOT NULL COMMENT 'API 动作（如 CreateFlow, StartFlow）',
    request_params  TEXT          NULL     COMMENT '请求参数 JSON',
    response_body   TEXT          NULL     COMMENT '响应体 JSON',
    status_code     INT           NULL     COMMENT 'HTTP 状态码',
    duration_ms     INT           NULL     COMMENT '调用耗时（毫秒）',
    error_message   VARCHAR(512)  NULL     COMMENT '错误信息',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_api_action (api_action),
    INDEX idx_created_at (created_at),
    INDEX idx_status_code (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='电子签 API 调用审计日志';
