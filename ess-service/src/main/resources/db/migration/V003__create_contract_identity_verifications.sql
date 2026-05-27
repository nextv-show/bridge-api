-- V003 创建 contract_identity_verifications 表
-- 合同签署前身份核验记录表

CREATE TABLE contract_identity_verifications (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id           VARCHAR(64)    NOT NULL COMMENT '内部合同 ID',
    user_id               BIGINT         NOT NULL COMMENT '用户 ID',
    kyc_record_id         VARCHAR(64)    NULL     COMMENT 'KYC 记录 ID',
    verification_type     VARCHAR(32)    NOT NULL DEFAULT 'FACE' COMMENT '核验类型: FACE/ID_CARD',
    ess_verification_id   VARCHAR(128)   NULL     COMMENT '腾讯电子签身份核验 ID',
    status                VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/IN_PROGRESS/PASSED/FAILED',
    face_score            DECIMAL(5,2)   NULL     COMMENT '人脸比对分数',
    verified_at           TIMESTAMP      NULL     COMMENT '核验通过时间',
    failure_reason        VARCHAR(512)   NULL     COMMENT '失败原因',
    retry_count           INT            NOT NULL DEFAULT 0 COMMENT '当日重试次数',
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_contract_id (contract_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='合同签署前身份核验记录';
