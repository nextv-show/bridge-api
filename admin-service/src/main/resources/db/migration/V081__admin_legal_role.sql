-- spec 006 #43: 新增 LEGAL 管理员角色（用于后台合同管理访问，含 PII），并 seed 一个法务账号。
-- admin-service 数据源实际为 h5_db；admin_users.role 为 MySQL ENUM，需扩充取值才能写入 'LEGAL'。

ALTER TABLE admin_users
  MODIFY COLUMN role ENUM('SUPER_ADMIN','FINANCE','OPS','READONLY','LEGAL') NOT NULL DEFAULT 'READONLY';

-- 默认法务账号: legal / Legal@2026。hash 由 Spring BCryptPasswordEncoder 生成（$2a$，rounds=10，
-- 与 V070 admin 同套编码器，已 matches 自验）。上线后请尽快改密。
INSERT INTO admin_users (username, password_hash, role)
VALUES ('legal', '$2a$10$sQm89o.jDnaBe88tsL6rIO3ZdxgjT8cjeY/aX3.jPDIo5Yb2EMb/e', 'LEGAL')
ON DUPLICATE KEY UPDATE role = 'LEGAL';
