-- 持久化商家转账商户单号（out_bill_no）。此前靠 PayoutBillNo.of(orderId,splitId) 确定性重算，
-- 一旦单号格式变更（如零填充修复），已被微信受理的在途单重算结果会与微信侧不一致，
-- 导致查单 WX_NOT_FOUND 误退款。受理时落库、查单优先读库（为空再回退重算）即可彻底免疫格式漂移。
-- MySQL 8.0.27 不支持 ADD COLUMN IF NOT EXISTS，用标准 ALTER。
ALTER TABLE withdrawal_splits
  ADD COLUMN out_bill_no VARCHAR(64) NULL AFTER external_id;
