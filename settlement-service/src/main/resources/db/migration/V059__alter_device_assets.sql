ALTER TABLE device_assets
  ADD COLUMN IF NOT EXISTS purchase_price_cents BIGINT NOT NULL DEFAULT 0 AFTER stage,
  ADD COLUMN IF NOT EXISTS promoter_user_id     BIGINT NULL AFTER roi_bp,
  ADD INDEX IF NOT EXISTS idx_promoter (promoter_user_id),
  ADD INDEX IF NOT EXISTS idx_stage_roi (stage, roi_bp);
