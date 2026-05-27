-- V004 创建 contract_templates 表
-- 合同模板管理表，存储主合同与附件模板

CREATE TABLE contract_templates (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code         VARCHAR(64)    NOT NULL COMMENT '模板编码，如 MAIN_CONTRACT / PROPERTY_CERT',
    template_name         VARCHAR(256)   NOT NULL COMMENT '模板名称',
    template_type         VARCHAR(32)    NOT NULL DEFAULT 'MAIN' COMMENT '模板类型: MAIN/ATTACHMENT',
    content_body          TEXT           NOT NULL COMMENT '模板内容（支持占位符）',
    version               INT            NOT NULL DEFAULT 1 COMMENT '模板版本号',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_template_code_version (template_code, version),
    INDEX idx_template_code (template_code),
    INDEX idx_template_type (template_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同模板管理表';
