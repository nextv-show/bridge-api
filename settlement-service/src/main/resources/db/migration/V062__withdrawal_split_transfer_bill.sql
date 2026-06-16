-- 商家转账 V3（transfer-bills）：记录微信转账单号 + 用户确认 package_info。
-- MySQL 8.0.27 不支持 ADD COLUMN IF NOT EXISTS，用标准 ALTER。
ALTER TABLE withdrawal_splits
  ADD COLUMN transfer_bill_no VARCHAR(64) NULL AFTER external_id,
  ADD COLUMN package_info VARCHAR(2048) NULL AFTER transfer_bill_no;
