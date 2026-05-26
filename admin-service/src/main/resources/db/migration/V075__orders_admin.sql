-- Add order management columns for admin module.
-- MySQL 8 不支持 ADD COLUMN / CREATE INDEX 的 IF NOT EXISTS 语法；这些列与索引均为本迁移首次引入，直接创建。
ALTER TABLE orders
  ADD COLUMN channel VARCHAR(16) NULL AFTER status,
  ADD COLUMN payment_method VARCHAR(16) NULL AFTER channel,
  ADD COLUMN device_asset_id BIGINT NULL AFTER wx_transaction_id,
  ADD COLUMN shipping_no VARCHAR(64) NULL AFTER device_asset_id,
  ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER shipping_no,
  ADD COLUMN shipped_at DATETIME NULL AFTER paid_at,
  ADD COLUMN delivered_at DATETIME NULL AFTER shipped_at,
  ADD COLUMN cancelled_at DATETIME NULL AFTER delivered_at,
  ADD COLUMN updated_at DATETIME NULL AFTER cancelled_at;

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_channel ON orders(channel);
CREATE INDEX idx_orders_payment_method ON orders(payment_method);
CREATE INDEX idx_orders_created_at ON orders(created_at);
