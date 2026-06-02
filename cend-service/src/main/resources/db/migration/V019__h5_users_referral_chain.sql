-- V019 H5 用户身份表 + 关系链（008b-referral-binding）。
-- 背景：canonical users 表归未部署的 user-service（user_db，见 008a T8a.1）；cend-service 以 openid 自有认证。
-- 为使 H5 推广关系链在「仅部署 cend-service」的真实生产环境可落地，cend-service 在 h5_db 维护自有轻量身份表。
-- 自增 id 即 ref_id 编码的 H5 user_id；inviter_id(L1)+grand_inviter_id(L2) 仅首次注册写入，已注册不可改。
--
-- L3+ 物理隔离：不为 grand_inviter_id 建索引（该列仅作单条快照存储，严禁以其为条件向上递归查询）。
-- 表名 h5_users 刻意区别于 admin-service 在 h5_db 的去规范化镜像 users（V074），避免实体/迁移冲突。
CREATE TABLE h5_users (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid           VARCHAR(64) NOT NULL UNIQUE COMMENT '微信 openid（H5 用户唯一身份键）',
  inviter_id       BIGINT NULL COMMENT 'L1 邀请人 H5 user_id（自然流量为 null）；仅首次注册写入',
  grand_inviter_id BIGINT NULL COMMENT 'L2 间接邀请人 H5 user_id（可 null）；仅快照，严禁向上递归（L3 隔离）',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_inviter (inviter_id)
);
