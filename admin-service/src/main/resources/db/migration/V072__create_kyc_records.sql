CREATE TABLE IF NOT EXISTS kyc_records (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  real_name       VARCHAR(128) NOT NULL,
  id_number       VARCHAR(128) NOT NULL,
  status          ENUM('PENDING','PASS','REJECT') NOT NULL DEFAULT 'PENDING',
  reviewed_by     BIGINT,
  reviewed_at     DATETIME,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_status (status)
);
