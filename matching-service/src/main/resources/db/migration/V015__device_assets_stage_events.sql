-- 002 设备阶段事件流（plan §4 / §6 / §8-#2）：供 003-water-iot 监听。
-- 003 用设备 MQTT 心跳触发 STAGE_1，与本表不冲突；002 仅建表+写入。
CREATE TABLE device_assets_stage_events (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_asset_id BIGINT NOT NULL,
  event_type      VARCHAR(32) NOT NULL,
  payload_json    JSON,
  occurred_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_device (device_asset_id, occurred_at)
);
