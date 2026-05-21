CREATE TABLE IF NOT EXISTS admin_users (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('SUPER_ADMIN','FINANCE','OPS','READONLY') NOT NULL DEFAULT 'READONLY',
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 默认管理员: admin / Admin@2026
-- BCrypt hash generated with rounds=10
INSERT INTO admin_users (username, password_hash, role)
VALUES ('admin', '$2a$10$/7pLY2Tffz1/SKXBxrsomuwR0dOh6OL87socmgIA94gAtDvRhDZbm', 'SUPER_ADMIN');
