-- V004 关系链 + 多端识别键（008a-referral-schema-codec）。
-- canonical users 表（user_db）承载关系链：L1 邀请人 + L2 间接邀请人，物理限制最多两级（L1+L2 死锁）。
-- 所有列可空，兼容存量自然流量用户；绑定仅在首次注册写入（绑定逻辑见 008b）。
-- 严禁以 grand_inviter_id 为查询条件做向上递归（L3+ 物理隔离 schema 前提）。
ALTER TABLE users ADD COLUMN inviter_id BIGINT NULL COMMENT 'L1 邀请人 user_id（自然流量为 null）';
ALTER TABLE users ADD COLUMN grand_inviter_id BIGINT NULL COMMENT 'L2 间接邀请人 user_id（可 null）';
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL COMMENT '多端账号统一识别键（手机号，可 null）';

CREATE INDEX idx_inviter ON users (inviter_id);
CREATE INDEX idx_grand_inviter ON users (grand_inviter_id);
