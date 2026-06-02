-- 002 C.1：接单占用唯一性收敛为「仅活跃（released_at IS NULL）」语义（FR-3 接单 / FR-5.3 释放后可重接）。
--
-- V011 的两条唯一键存在缺陷：
--   uk_request(request_id)               —— 跨「已释放」历史行也唯一，导致释放后无法重新接单。
--   uk_device_active(device_asset_id, released_at) —— MySQL 唯一索引视多个 NULL 互异，
--                                          同一设备可存在多条 released_at=NULL 的活跃行，约束形同虚设。
--
-- 改用生成列 active_flag：活跃行(=1)参与唯一约束，已释放行(=NULL)退出约束。
-- 于是「同需求 / 同设备至多一条活跃占用，释放后可重新接单」原子地由 DB 保证。
-- 注：active_flag 不在 JPA 实体映射中；ddl-auto=validate 仅校验实体列存在，不因表多出列而失败。

ALTER TABLE matching_assignments
  DROP INDEX uk_request,
  DROP INDEX uk_device_active;

ALTER TABLE matching_assignments
  ADD COLUMN active_flag TINYINT
    GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN 1 ELSE NULL END) VIRTUAL;

ALTER TABLE matching_assignments
  ADD UNIQUE KEY uk_request_active (request_id, active_flag),     -- 一单至多一条活跃占用
  ADD UNIQUE KEY uk_device_active (device_asset_id, active_flag); -- 一设备至多一条活跃占用
