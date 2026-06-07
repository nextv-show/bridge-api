CREATE TABLE device_telemetry_flow (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn             VARCHAR(64) NOT NULL,
  session_id     BIGINT NULL,
  liters_milli   BIGINT NOT NULL COMMENT 'cumulative milliliters at this sample',
  delta_milli    BIGINT NOT NULL COMMENT 'increment since last sample',
  sampled_at     DATETIME(3) NOT NULL,
  received_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sn_time (sn, sampled_at),
  KEY idx_session (session_id, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
