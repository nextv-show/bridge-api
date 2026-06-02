-- 002 物流状态流水（plan §4）。external_event_id 唯一 → 回调幂等（FR-4.4）。
CREATE TABLE logistics_events (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  logistics_order_id  BIGINT NOT NULL,
  event_type          VARCHAR(32) NOT NULL,
  payload_json        JSON,
  external_event_id   VARCHAR(128),
  occurred_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_external (external_event_id),
  KEY idx_order (logistics_order_id, occurred_at)
);
