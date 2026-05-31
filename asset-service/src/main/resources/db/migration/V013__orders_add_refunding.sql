-- V013 购机订单状态机新增 REFUNDING（退款中）。
-- 状态机：PAID --requestRefund--> REFUNDING --回调/查单成功--> REFUND；REFUNDING --失败--> PAID。
ALTER TABLE orders
  MODIFY COLUMN status ENUM('PENDING_PAY','PAID','CLOSED','REFUND','REFUNDING') NOT NULL;
