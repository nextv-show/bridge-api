CREATE TABLE device_stage_history (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  sn           VARCHAR(64) NOT NULL,
  from_stage   ENUM('PENDING_MATCH','STAGE_1','STAGE_2') NOT NULL,
  to_stage     ENUM('STAGE_1','STAGE_2') NOT NULL,
  at_bill_id   BIGINT NOT NULL,
  at_roi_bp    INT NOT NULL,
  occurred_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sn_to_stage_bill (sn, to_stage, at_bill_id),
  KEY idx_sn_time (sn, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
