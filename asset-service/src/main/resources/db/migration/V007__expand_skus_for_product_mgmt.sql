-- V007: Expand skus table for full product management module
-- Aligns with the admin prototype design (商品管理)

-- 1. Add new columns
ALTER TABLE skus
  ADD COLUMN code           VARCHAR(32)                      AFTER name,
  ADD COLUMN subtitle       VARCHAR(255)                     AFTER code,
  ADD COLUMN category       VARCHAR(32) NOT NULL DEFAULT 'HOME' AFTER subtitle,
  ADD COLUMN original_cents BIGINT NOT NULL DEFAULT 0        AFTER price_cents,
  ADD COLUMN cost_cents     BIGINT NOT NULL DEFAULT 0        AFTER original_cents,
  ADD COLUMN stock          INT NOT NULL DEFAULT 0           AFTER deposit_cents,
  ADD COLUMN stock_warn     INT NOT NULL DEFAULT 0           AFTER stock,
  ADD COLUMN sold_30d       INT NOT NULL DEFAULT 0           AFTER stock_warn,
  ADD COLUMN s1_months      INT NOT NULL DEFAULT 0           AFTER sold_30d,
  ADD COLUMN s2_months      INT NOT NULL DEFAULT 0           AFTER s1_months,
  ADD COLUMN fuse_at        INT NOT NULL DEFAULT 0           AFTER s2_months,
  ADD COLUMN annualized_bp  INT NOT NULL DEFAULT 0           AFTER fuse_at,
  ADD COLUMN sold_total     INT NOT NULL DEFAULT 0           AFTER annualized_bp,
  ADD COLUMN refund_rate    DECIMAL(6,4) NOT NULL DEFAULT 0  AFTER sold_total,
  ADD COLUMN gmv_cents      BIGINT NOT NULL DEFAULT 0        AFTER refund_rate,
  ADD COLUMN conversion     DECIMAL(6,4) NOT NULL DEFAULT 0  AFTER gmv_cents,
  ADD COLUMN accent         VARCHAR(16) NOT NULL DEFAULT '#5BA8FF' AFTER conversion,
  ADD COLUMN featured       BOOLEAN NOT NULL DEFAULT FALSE    AFTER accent,
  ADD COLUMN low_stock      BOOLEAN NOT NULL DEFAULT FALSE    AFTER featured,
  ADD COLUMN no_stage       BOOLEAN NOT NULL DEFAULT FALSE    AFTER low_stock,
  ADD COLUMN draft_col      BOOLEAN NOT NULL DEFAULT FALSE    AFTER no_stage,
  ADD COLUMN note           TEXT                              AFTER draft_col;

-- 2. Widen status ENUM to support prototype states (ACTIVE, PAUSED, OFFLINE, DRAFT)
ALTER TABLE skus MODIFY COLUMN status ENUM('ACTIVE','PAUSED','OFFLINE','DRAFT','INACTIVE') NOT NULL DEFAULT 'ACTIVE';

-- 3. Seed prototype SKUs ONLY when the table is empty (fresh / dev DB).
--    Idempotent and non-destructive: never deletes or overwrites existing data,
--    and never adds demo rows to a database that already holds real SKUs.
--    The target table is referenced through a derived table to avoid MySQL
--    error 1093 (can't read & write the same table in one statement).
INSERT INTO skus (name, code, subtitle, category, price_cents, original_cents, cost_cents,
  deposit_cents, stock, stock_warn, sold_30d, s1_months, s2_months, fuse_at, annualized_bp,
  sold_total, refund_rate, gmv_cents, conversion, accent, featured, low_stock, draft_col,
  status, benefits_md, image_url, created_at, updated_at)
SELECT * FROM (
  SELECT '家庭版·标准型' AS name, 'BR-H1' AS code, '日产水 200L · 适配 3-4 口之家' AS subtitle, 'HOME' AS category,
         460000 AS price_cents, 520000 AS original_cents, 228000 AS cost_cents, 0 AS deposit_cents,
         462 AS stock, 100 AS stock_warn, 38 AS sold_30d, 12 AS s1_months, 24 AS s2_months, 36 AS fuse_at, 850 AS annualized_bp,
         38 AS sold_total, 0.0130 AS refund_rate, 17480000 AS gmv_cents, 0.0720 AS conversion, '#28805c' AS accent,
         FALSE AS featured, FALSE AS low_stock, FALSE AS draft_col, 'ACTIVE' AS status,
         '基础净水设备，适合家庭日常饮水' AS benefits_md, 'https://example.com/sku1.png' AS image_url, NOW() AS created_at, NOW() AS updated_at
  UNION ALL SELECT '家庭版·增强型', 'BR-H2', '日产水 400L · 双膜净化', 'HOME',
         680000, 760000, 336000, 0, 288, 100, 56, 12, 24, 36, 850,
         56, 0.0070, 38080000, 0.0940, '#5BA8FF', TRUE, FALSE, FALSE, 'ACTIVE',
         '双膜净化，大容量家庭用水', 'https://example.com/sku2.png', NOW(), NOW()
  UNION ALL SELECT '商用 A 型', 'BR-C1', '日产水 1.2T · 小型商铺/工作室', 'COMMERCIAL',
         860000, 960000, 425000, 0, 164, 80, 34, 12, 24, 36, 850,
         34, 0.0180, 29240000, 0.0610, '#d4a847', FALSE, FALSE, FALSE, 'ACTIVE',
         '商用净水方案，适合小型商业场景', 'https://example.com/sku3.png', NOW(), NOW()
  UNION ALL SELECT '商用 B 型', 'BR-C2', '日产水 3T · 餐饮/中型场景', 'COMMERCIAL',
         1280000, 0, 632000, 0, 42, 50, 14, 12, 24, 36, 850,
         14, 0.0210, 17920000, 0.0380, '#9d6dff', FALSE, TRUE, FALSE, 'ACTIVE',
         '大容量商用净水，适合餐饮等中型场景', 'https://example.com/sku4.png', NOW(), NOW()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM skus LIMIT 1) AS existing);
