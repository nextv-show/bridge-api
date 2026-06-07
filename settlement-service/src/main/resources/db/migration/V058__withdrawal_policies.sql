CREATE TABLE withdrawal_policies (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  fee_bp            INT  NOT NULL DEFAULT 200,
  single_max_cents  BIGINT NOT NULL DEFAULT 5000000,
  daily_max_cents   BIGINT NOT NULL DEFAULT 10000000,
  effective_from    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO withdrawal_policies () VALUES ();
