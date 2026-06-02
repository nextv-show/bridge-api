CREATE TABLE kyc_records (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid          VARCHAR(64) NOT NULL,
  real_name       VARBINARY(256),
  id_card_no_enc  VARBINARY(256) NOT NULL,
  id_card_no_mask VARCHAR(32) NOT NULL,
  real_name_mask  VARCHAR(32) NOT NULL,
  certify_id      VARCHAR(64),
  channel         VARCHAR(32) NOT NULL DEFAULT 'ALIYUN_FINANCE',
  status          ENUM('INIT','PASS','FAIL','SUPERSEDED') NOT NULL DEFAULT 'INIT',
  verified_at     DATETIME,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_openid_status (openid, status)
);
