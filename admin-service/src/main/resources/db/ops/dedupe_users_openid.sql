-- ════════════════════════════════════════════════════════════════════════════
-- #19 一次性去重：合并 users 表中 openid 重复的行，使 V080 的 uk_openid 可建。
--
-- ⚠️ 这是 OPS 一次性 DML，**不是 Flyway 迁移**（故置于 db/ops/，不在 classpath:db/migration，
--    Flyway 不会扫到/重放）。仅在**生产**（及任何可能已有重复 openid 的环境）由人工执行一次。
-- ⚠️ 执行前置：① 低峰窗口；② 先整库/至少相关表备份（users / orders / device_assets /
--    matching_requests / matching_assignments）；③ 在事务内执行，校验通过再 COMMIT。
-- ⚠️ 顺序：先跑本脚本 → 再部署 admin-service（触发 V080 加唯一键）。
--
-- 库：core_db。保留策略：每个重复 openid 保留 MIN(id)，其余行的引用重指到保留 id 后删除。
-- 引用 users.id（user_id 系列）的表（截至 002 Phase B）：
--   orders.user_id、device_assets.user_id、
--   matching_requests.user_id、matching_requests.locked_by_user_id、
--   matching_assignments.owner_user_id
-- 注：h5_users（含 inviter_id/grand_inviter_id）引用的是 h5_users.id，**不**是 users.id，无需处理。
--    若后续新增以 users.id 为外键的表，须在此补充对应 UPDATE。
-- ════════════════════════════════════════════════════════════════════════════

START TRANSACTION;

-- 0) 预检：列出将被合并的重复 openid（执行前肉眼确认规模）。
SELECT openid, COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id) AS ids
FROM users
WHERE openid IS NOT NULL
GROUP BY openid
HAVING cnt > 1;

-- 1) 构建映射：dup_id（待删）-> keep_id（每个重复 openid 的最小 id）。
DROP TEMPORARY TABLE IF EXISTS _users_openid_remap;
CREATE TEMPORARY TABLE _users_openid_remap AS
SELECT u.id AS dup_id, k.keep_id
FROM users u
JOIN (
    SELECT openid, MIN(id) AS keep_id
    FROM users
    WHERE openid IS NOT NULL
    GROUP BY openid
    HAVING COUNT(*) > 1
) k ON u.openid = k.openid
WHERE u.id <> k.keep_id;

-- 2) 重指引用（每条 UPDATE 只引用临时表一次，符合 MySQL 限制）。
UPDATE orders o
    JOIN _users_openid_remap m ON o.user_id = m.dup_id
    SET o.user_id = m.keep_id;

UPDATE device_assets d
    JOIN _users_openid_remap m ON d.user_id = m.dup_id
    SET d.user_id = m.keep_id;

UPDATE matching_requests r
    JOIN _users_openid_remap m ON r.user_id = m.dup_id
    SET r.user_id = m.keep_id;

UPDATE matching_requests r
    JOIN _users_openid_remap m ON r.locked_by_user_id = m.dup_id
    SET r.locked_by_user_id = m.keep_id;

UPDATE matching_assignments a
    JOIN _users_openid_remap m ON a.owner_user_id = m.dup_id
    SET a.owner_user_id = m.keep_id;

-- 3) 删除冗余 users 行。
DELETE u FROM users u
    JOIN _users_openid_remap m ON u.id = m.dup_id;

-- 4) 校验：以下两条都应返回 0 行，否则 ROLLBACK 排查。
SELECT openid, COUNT(*) AS cnt FROM users WHERE openid IS NOT NULL GROUP BY openid HAVING cnt > 1;
SELECT COUNT(*) AS orphan_remap_left FROM _users_openid_remap m
    JOIN users u ON u.id = m.dup_id;  -- 仍存在未删的 dup → 异常

DROP TEMPORARY TABLE IF EXISTS _users_openid_remap;

-- 校验通过后：COMMIT;   失败：ROLLBACK;
-- （脚本默认不自动 COMMIT，由执行人确认 4) 结果后手动提交。）
