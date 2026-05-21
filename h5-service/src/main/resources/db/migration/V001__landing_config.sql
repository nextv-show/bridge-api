-- V001 落地页主配置（schema: h5_db）
-- status/version/updated_by/published_at 为 105 后台「写/发布/回滚」预留；本模块只读 PUBLISHED。
-- hero/simulator/footer 以 JSON 列存储，便于 105 整体编辑；feature/trust badge 走独立子表（决策门 G-3）。
CREATE TABLE landing_config (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  version        INT          NOT NULL DEFAULT 1,
  status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT / PUBLISHED / ARCHIVED
  hero_json      JSON         NOT NULL,                   -- {logoUrl, titleLines[], subtitle, kpis[], industries[]}
  simulator_json JSON         NOT NULL,                   -- {minLiters, maxLiters, defaultLiters, baseRateBp, networkBonusBp, bonusThresholdLiters, unit, outputLabel, disclaimer}
  footer_json    JSON         NOT NULL,                   -- {disclaimer, icpNumber}
  updated_by     VARCHAR(64)  NULL,
  published_at   DATETIME     NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 业务约束：同一时刻仅一条 status='PUBLISHED'（由 105 发布逻辑保证；本模块只读最新一条 PUBLISHED）。
