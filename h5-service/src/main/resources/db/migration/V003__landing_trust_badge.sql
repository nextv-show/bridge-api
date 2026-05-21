-- V003 区块03「购置保障」TrustBadge 子表（独立子表，决策门 G-3）
CREATE TABLE landing_trust_badge (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  config_id BIGINT       NOT NULL,
  sort      INT          NOT NULL DEFAULT 0,
  title     VARCHAR(64)  NOT NULL,    -- 唯一 SN 码 / 13% 增值税专票 / 24h 冷静期 / 第三方资金托管
  subtitle  VARCHAR(64)  NULL,        -- 一机一码 / 一机一发票 / 无理由退款 / 全程加密
  icon_key  VARCHAR(32)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_badge_config (config_id, sort),
  CONSTRAINT fk_badge_config FOREIGN KEY (config_id) REFERENCES landing_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
