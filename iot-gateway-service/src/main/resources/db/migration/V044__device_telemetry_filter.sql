CREATE TABLE device_telemetry_filter (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn           VARCHAR(64) NOT NULL,
  life_percent SMALLINT NOT NULL COMMENT '0-100 filter life remaining',
  sampled_at   DATETIME(3) NOT NULL,
  received_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sn_time (sn, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
