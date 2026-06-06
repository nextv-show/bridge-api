CREATE TABLE device_telemetry_tds (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn          VARCHAR(64) NOT NULL,
  tds_value   INT NOT NULL,
  sampled_at  DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sn_time (sn, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
