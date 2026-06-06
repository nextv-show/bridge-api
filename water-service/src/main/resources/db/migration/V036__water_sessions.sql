CREATE TABLE water_sessions (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn                  VARCHAR(64) NOT NULL,
  user_id             BIGINT NOT NULL,
  status              ENUM('ACTIVE','CLOSED','ABORTED') NOT NULL DEFAULT 'ACTIVE',
  started_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at            DATETIME,
  total_liters_milli  BIGINT NOT NULL DEFAULT 0,
  total_amount_cents  BIGINT NOT NULL DEFAULT 0,
  price_per_liter_cents INT NOT NULL,
  end_reason          ENUM('USER_STOP','BALANCE_OUT','DEVICE_LIMIT','TIMEOUT','ERROR'),
  version             INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sn_active (sn, status),            -- 同 SN 同时只允许一个 ACTIVE
  KEY idx_user_status (user_id, status),
  KEY idx_started (started_at)
);
