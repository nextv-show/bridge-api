-- Add payment control fields for order admin actions.
-- MySQL 8 不支持 ADD COLUMN 的 IF NOT EXISTS 语法；这两列为本迁移首次引入，直接创建。
ALTER TABLE orders
  ADD COLUMN payment_deadline_at DATETIME NULL AFTER paid_at,
  ADD COLUMN last_reminded_at DATETIME NULL AFTER payment_deadline_at;
