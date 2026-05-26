-- Add order management columns for admin module.
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS channel VARCHAR(16) NULL AFTER status,
  ADD COLUMN IF NOT EXISTS payment_method VARCHAR(16) NULL AFTER channel,
  ADD COLUMN IF NOT EXISTS device_asset_id BIGINT NULL AFTER wx_transaction_id,
  ADD COLUMN IF NOT EXISTS shipping_no VARCHAR(64) NULL AFTER device_asset_id,
  ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(255) NULL AFTER shipping_no,
  ADD COLUMN IF NOT EXISTS shipped_at DATETIME NULL AFTER paid_at,
  ADD COLUMN IF NOT EXISTS delivered_at DATETIME NULL AFTER shipped_at,
  ADD COLUMN IF NOT EXISTS cancelled_at DATETIME NULL AFTER delivered_at,
  ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL AFTER cancelled_at;

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_channel ON orders(channel);
CREATE INDEX IF NOT EXISTS idx_orders_payment_method ON orders(payment_method);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
