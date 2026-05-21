-- V002 区块01「核心能力」FeatureCard 子表（独立子表，决策门 G-3）
CREATE TABLE landing_feature (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  config_id BIGINT       NOT NULL,
  sort      INT          NOT NULL DEFAULT 0,
  title     VARCHAR(64)  NOT NULL,    -- 远程水质回传 / 故障自检 / 服务返利 / 24h 冷静期
  subtitle  VARCHAR(64)  NULL,        -- mono 英文小标，如 REMOTE TDS
  descr     VARCHAR(255) NULL,
  icon_key  VARCHAR(32)  NOT NULL,    -- 前端按 key 映射内联 SVG
  PRIMARY KEY (id),
  KEY idx_feature_config (config_id, sort),
  CONSTRAINT fk_feature_config FOREIGN KEY (config_id) REFERENCES landing_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
