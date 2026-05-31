-- V012 购机订单退款记录（asset_db）—— 冷静期无理由退款（实体商品买卖合同解除）。
-- 一订单一退款（uk_order）；refund_no = 商户侧 out_refund_no，全局唯一（uk_refund_no）。
-- 列类型遵循既有约定：status 以 @Enumerated(STRING) 映射 VARCHAR（非 MySQL ENUM），ddl-auto=validate。
CREATE TABLE refunds (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT NOT NULL COMMENT '退款订单 id（orders.id），一订单一退款',
  refund_no     VARCHAR(64) NOT NULL COMMENT '商户退款单号 out_refund_no（RF+epochMillis+orderId）',
  wx_refund_id  VARCHAR(64) COMMENT '微信退款单号 refund_id（成功后回填）',
  amount_cents  BIGINT NOT NULL COMMENT '退款金额（分），全额退',
  status        VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCESS/FAILED',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  succeeded_at  DATETIME COMMENT '退款成功时刻',
  UNIQUE KEY uk_order (order_id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_status (status)
);
