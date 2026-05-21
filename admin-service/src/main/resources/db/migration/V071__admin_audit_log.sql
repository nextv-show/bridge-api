CREATE TABLE IF NOT EXISTS admin_audit_log (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id     BIGINT NOT NULL,
  action       VARCHAR(64) NOT NULL,
  target_type  VARCHAR(64) NOT NULL,
  target_id    VARCHAR(128) NOT NULL,
  payload_json JSON,
  ip_address   VARCHAR(45),
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_admin_time (admin_id, created_at),
  KEY idx_target (target_type, target_id),
  KEY idx_action_time (action, created_at)
);
