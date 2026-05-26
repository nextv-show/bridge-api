-- Add payment control fields for order admin actions.
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS payment_deadline_at DATETIME NULL AFTER paid_at,
  ADD COLUMN IF NOT EXISTS last_reminded_at DATETIME NULL AFTER payment_deadline_at;
