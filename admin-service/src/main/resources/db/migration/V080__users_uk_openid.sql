-- #19：修「并发首触同一 openid → 重复 users 行」。给 users.openid 加唯一键。
-- 目标库：h5_db（admin-service 的 Flyway 实际连 h5_db，与 V074/V079 同库）。
--
-- ⚠️ 生产前置：本迁移仅加约束，**不去重**。若库内已存在重复 openid，ADD UNIQUE KEY 会失败
--    （这是安全失败——不会静默吞掉数据）。生产部署前须先手工执行去重 ops 脚本：
--      db/ops/dedupe_users_openid.sql（一次性 DML，NOT Flyway，参照 024 C.5 先例）。
--    去重脚本把重复 openid 的引用（orders/device_assets/matching_requests/matching_assignments
--    的 user_id 系列列）重指到保留行后删冗余，再跑本迁移。
--
-- 说明：openid 可空；MySQL 唯一键允许多个 NULL，不影响 openid 为 NULL 的行（现网 resolveUserId
--      与 V074 seed 均写入非空 openid，理论上无 NULL）。新建/测试环境无重复，本迁移直接通过。

ALTER TABLE users ADD UNIQUE KEY uk_openid (openid);
