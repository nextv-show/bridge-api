CREATE TABLE device_alarms (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn              VARCHAR(64) NOT NULL,
  alarm_type      ENUM('LEAK','TDS_ABNORMAL','FILTER_LOW','OFFLINE_TOO_LONG','FILTER_DRIFT','OTHER') NOT NULL,
  severity        ENUM('INFO','WARN','CRITICAL') NOT NULL DEFAULT 'WARN',
  external_event_id VARCHAR(128) NULL COMMENT 'idempotency key from device event',
  payload_json    JSON NULL,
  raised_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at     DATETIME(3) NULL,
  UNIQUE KEY uk_external_event (external_event_id),
  KEY idx_sn_time (sn, raised_at),
  KEY idx_open (resolved_at, alarm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
