-- 002 跨服务 outbox（plan §4）：接单同事务写，logistics-service 拉取建工单。
CREATE TABLE logistics_outbox (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id      BIGINT NOT NULL,
  device_asset_id BIGINT NOT NULL,
  payload_json    JSON NOT NULL,                  -- 含 ship_to_address 快照（来源：用水点地址 matching_requests.address）
  consumed_at     DATETIME,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_pending (consumed_at, created_at)
);
