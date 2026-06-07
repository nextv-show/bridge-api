CREATE TABLE owner_wallets (
  user_id        BIGINT PRIMARY KEY,
  balance_cents  BIGINT NOT NULL DEFAULT 0,
  frozen_cents   BIGINT NOT NULL DEFAULT 0,
  version        INT NOT NULL DEFAULT 0,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_balance_nonneg CHECK (balance_cents >= 0),
  CONSTRAINT chk_frozen_nonneg  CHECK (frozen_cents  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
