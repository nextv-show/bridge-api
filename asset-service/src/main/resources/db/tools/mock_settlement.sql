-- =============================================================================
-- E.1 mock-settlement：模拟 004-settlement-engine 对 device_assets 的写入，
-- 供 E 阶段（回本/熔断展示）与 D.5 联调使用。
--
-- ⚠️ 仅测试库执行。按 G-1 写入边界，stage / cumulative_income_cents / roi_bp 三列
-- 的写入归 settlement 账号，故本脚本应以 'settlement' 账号连接执行（见
-- db/grants/device_assets_grants.sql）。本模块业务账号 asset_app 无权改这三列。
--
-- roi_bp = 累计收益 / 购机款 × 10000（基点）；20000 = 200% = 熔断阈值。
-- 用法：把下方 :sn 替换为目标设备 SN，或按需改 WHERE 条件批量更新。
-- =============================================================================

-- 1) 推进到「回本前」STAGE_1：有收益、未回本（roi_bp < 10000）
UPDATE device_assets
   SET stage = 'STAGE_1',
       cumulative_income_cents = 30000,   -- ¥300
       roi_bp = 3000                      -- 30%
 WHERE sn = 'MOCK-SN-0001';

-- 2) 推进到「回本后」STAGE_2：已回本、未熔断（10000 <= roi_bp < 20000）
UPDATE device_assets
   SET stage = 'STAGE_2',
       cumulative_income_cents = 150000,  -- ¥1500
       roi_bp = 15000                     -- 150%
 WHERE sn = 'MOCK-SN-0002';

-- 3) 触发「已熔断」FUSED：roi_bp >= 20000（E.3 用例）
UPDATE device_assets
   SET stage = 'FUSED',
       cumulative_income_cents = 220000,  -- ¥2200
       roi_bp = 22000                     -- 220%
 WHERE sn = 'MOCK-SN-0003';

-- 批量示例：把某用户名下所有 STAGE_2 设备熔断
-- UPDATE device_assets
--    SET stage = 'FUSED', roi_bp = 20000
--  WHERE user_id = :userId AND stage = 'STAGE_2';
