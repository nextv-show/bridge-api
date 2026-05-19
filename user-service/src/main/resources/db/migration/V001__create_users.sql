CREATE TABLE users (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  unionid       VARCHAR(64) UNIQUE,
  openid_wx     VARCHAR(64),
  openid_app    VARCHAR(64),
  nickname      VARCHAR(64),
  avatar_url    VARCHAR(512),
  active_role   ENUM('CONSUMER','OWNER','PROMOTER') NOT NULL DEFAULT 'CONSUMER',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_openid_wx (openid_wx),
  KEY idx_openid_app (openid_app)
);
