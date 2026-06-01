-- 002 接单绑定（plan §4）。
CREATE TABLE matching_assignments (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id       BIGINT NOT NULL,
  device_asset_id  BIGINT NOT NULL,
  owner_user_id    BIGINT NOT NULL,
  locked_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  released_at      DATETIME,
  UNIQUE KEY uk_request (request_id),                          -- 一单一占
  UNIQUE KEY uk_device_active (device_asset_id, released_at),  -- 设备未释放前唯一
  KEY idx_owner (owner_user_id, released_at)
);
