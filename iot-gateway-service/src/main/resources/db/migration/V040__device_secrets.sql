CREATE TABLE device_secrets (
  sn             VARCHAR(64) PRIMARY KEY,
  secret_enc     VARBINARY(255) NOT NULL COMMENT 'AES-GCM encrypted device secret',
  issued_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at     DATETIME NULL,
  KEY idx_issued (issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
