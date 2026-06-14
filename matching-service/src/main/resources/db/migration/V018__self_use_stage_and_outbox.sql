-- 027-self-use-path：设备自用路径 + outbox source 字段

-- 1) device_assets.stage ENUM 扩展：加 SELF_USE 和 LOCKED（补历史遗漏）
ALTER TABLE device_assets
  MODIFY COLUMN stage ENUM('PENDING_MATCH','SELF_USE','LOCKED','PENDING_ACTIVATE','STAGE_1','STAGE_2','FUSED')
  NOT NULL;

-- 2) logistics_outbox 增加 source 字段（区分匹配/自用来源）
ALTER TABLE logistics_outbox
  ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MATCHING' AFTER device_asset_id;

-- 3) logistics_outbox.request_id 允许 NULL（自用场景无匹配需求）
ALTER TABLE logistics_outbox
  MODIFY COLUMN request_id BIGINT NULL;
