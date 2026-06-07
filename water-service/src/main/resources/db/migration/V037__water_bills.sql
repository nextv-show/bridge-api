CREATE TABLE water_bills (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id            BIGINT NOT NULL UNIQUE,
  sn                    VARCHAR(64) NOT NULL,
  user_id               BIGINT NOT NULL,
  liters_milli          BIGINT NOT NULL,
  price_per_liter_cents INT NOT NULL,
  amount_cents          BIGINT NOT NULL,
  settled_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  chain_status          ENUM('PENDING','ON_CHAIN','FAILED') NOT NULL DEFAULT 'PENDING',
  chain_tx_hash         VARCHAR(128),
  chain_retried         INT NOT NULL DEFAULT 0,
  KEY idx_sn_time (sn, settled_at),
  KEY idx_user_time (user_id, settled_at),
  KEY idx_chain (chain_status, chain_retried)
);
