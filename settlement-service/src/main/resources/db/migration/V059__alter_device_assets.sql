-- 注意：MySQL 8.0 不支持 ALTER TABLE ... ADD COLUMN/INDEX IF NOT EXISTS（MariaDB 语法）。
-- Flyway 对每个迁移仅执行一次，无需 IF NOT EXISTS 守卫。
ALTER TABLE device_assets
  ADD COLUMN purchase_price_cents BIGINT NOT NULL DEFAULT 0 AFTER stage,
  ADD COLUMN promoter_user_id     BIGINT NULL AFTER roi_bp,
  ADD INDEX idx_promoter (promoter_user_id),
  ADD INDEX idx_stage_roi (stage, roi_bp);
