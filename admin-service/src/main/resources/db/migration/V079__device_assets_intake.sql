-- 024-device-asset-intake：认购支付完成的设备资产入库（001-user-asset 回归修复 delta）
-- 目标库 h5_db（admin-service 数据源）。真表 device_assets 由 admin V074 创建。
--
-- 1) sn 改为可空：SN 待绑定 = NULL（对齐 001 FR-5.3「待撮合」态）；原 NOT NULL 与待绑定语义矛盾。
--    MySQL UNIQUE 允许多个 NULL，故唯一性不受影响；真 SN 由 admin BindSnUseCase 后续绑定。
ALTER TABLE device_assets MODIFY COLUMN sn VARCHAR(64) NULL;

-- 2) order_id 唯一：认购单台（一单一机），作为「PAID → 建资产」的幂等键。
--    现有 4 行 demo seed 的 order_id 各异，不冲突。
ALTER TABLE device_assets ADD UNIQUE KEY uk_order (order_id);
