-- 002 物流工单（plan §4，落 h5_db）。ship_to 取自用水点地址（matching_requests.address）。
CREATE TABLE logistics_orders (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id               BIGINT NOT NULL,
  device_asset_id          BIGINT NOT NULL,
  ship_to_address_snapshot JSON NOT NULL,
  status                   ENUM('PENDING_SHIP','SHIPPED','DELIVERED','INSTALLED','CANCELLED') NOT NULL DEFAULT 'PENDING_SHIP',
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request (request_id),
  KEY idx_status (status)
);
