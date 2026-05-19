CREATE TABLE device_assets (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id                  BIGINT NOT NULL,
  order_id                 BIGINT NOT NULL,
  sn                       VARCHAR(64) UNIQUE,
  model                    VARCHAR(128) NOT NULL,
  purchased_at             DATETIME NOT NULL,
  stage                    ENUM('PENDING_MATCH','PENDING_ACTIVATE','STAGE_1','STAGE_2','FUSED') NOT NULL,
  cumulative_income_cents  BIGINT NOT NULL DEFAULT 0,
  roi_bp                   INT NOT NULL DEFAULT 0,
  KEY idx_user (user_id),
  KEY idx_order (order_id)
);
