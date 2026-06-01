-- 002 撮合需求单（plan §4，落 h5_db）。
CREATE TABLE matching_requests (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT NOT NULL,                 -- 发起人 h5_db.users.id（已登录，Q3）
  contact_name          VARCHAR(64) NOT NULL,
  contact_phone_enc     VARBINARY(255) NOT NULL,         -- AES-GCM，密钥同 h5
  contact_phone_hash    CHAR(64) NOT NULL,               -- HMAC-SHA256(规范化手机号)；明文不落库，供 24h-OPEN 计数/限频按手机号查
  address               VARCHAR(255) NOT NULL,
  lat                   DECIMAL(10,7) NOT NULL,
  lng                   DECIMAL(10,7) NOT NULL,
  geohash6              CHAR(6) NOT NULL,                -- 预生成 ~1km
  scene_type            ENUM('HOME','OFFICE','SHOP','CAMPUS') NOT NULL,
  est_daily_liters      INT NOT NULL,
  expected_price_tier   ENUM('T_040','T_080','T_120','T_150') NOT NULL,
  status                ENUM('OPEN','LOCKED','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'OPEN',
  locked_by_user_id     BIGINT,
  locked_at             DATETIME,
  version               INT NOT NULL DEFAULT 0,          -- 乐观锁
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_tier_geohash (status, expected_price_tier, geohash6),  -- Q1: tier 参与服务端过滤
  KEY idx_user (user_id),
  KEY idx_locked_by (locked_by_user_id, status),
  KEY idx_phone_hash_status_created (contact_phone_hash, status, created_at),  -- B.2.5 防刷：同手机号 24h-OPEN 计数
  KEY idx_status_lat (status, lat)  -- nearby bbox 半径查询（lat 范围扫描）
);
