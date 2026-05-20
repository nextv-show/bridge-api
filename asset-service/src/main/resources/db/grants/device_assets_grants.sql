-- =============================================================================
-- D.1.2 / G-1 写入边界：device_assets 的 cumulative_income_cents / roi_bp / stage
-- 三列只允许 004-settlement-engine 写入；本模块（asset-service）对这三列只读。
--
-- ⚠️ 本脚本不纳入 Flyway 迁移，由 DBA / 运维以管理员账号手动执行：
--   1) 应用 Flyway 账号不应、也不会被授予 CREATE USER / GRANT 权限；
--   2) Testcontainers / CI 用受限账号跑迁移，CREATE USER 会令整套迁移失败；
--   3) 账号口令属环境敏感项，绝不入 git（占位 <CHANGE_ME> 由运维替换或走密钥管理）。
--
-- 注：列写入边界由列级 UPDATE 授权保证。建资产时的初始 INSERT（stage=PENDING_MATCH,
-- roi_bp=0, cumulative_income_cents=0）由 asset_app 的表级 INSERT 完成，符合 G-1
-- “后续写入归 settlement” 的语义。
-- 执行环境：asset_db
-- =============================================================================

CREATE USER IF NOT EXISTS 'asset_app'@'%'  IDENTIFIED BY '<CHANGE_ME_ASSET_APP>';
CREATE USER IF NOT EXISTS 'settlement'@'%' IDENTIFIED BY '<CHANGE_ME_SETTLEMENT>';

-- ---- asset_app：本模块业务账号 ----
GRANT SELECT, INSERT, UPDATE, DELETE ON asset_db.skus          TO 'asset_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON asset_db.orders        TO 'asset_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON asset_db.payment_inbox TO 'asset_app'@'%';

-- device_assets：可读、可建（初始 INSERT）、可删；UPDATE 仅限非保护列
GRANT SELECT, INSERT, DELETE ON asset_db.device_assets TO 'asset_app'@'%';
GRANT UPDATE (user_id, order_id, sn, model, purchased_at)
    ON asset_db.device_assets TO 'asset_app'@'%';

-- ---- settlement：结算引擎账号，独占三保护列的写入 ----
GRANT SELECT ON asset_db.device_assets TO 'settlement'@'%';
GRANT UPDATE (cumulative_income_cents, roi_bp, stage)
    ON asset_db.device_assets TO 'settlement'@'%';

FLUSH PRIVILEGES;
