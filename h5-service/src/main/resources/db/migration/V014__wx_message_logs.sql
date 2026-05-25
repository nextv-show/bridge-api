-- Spec 106: wx_message_logs — 微信模板消息推送日志
-- 幂等键 uk_type_order 保证同类型同订单只推一次
CREATE TABLE wx_message_logs (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  openid       VARCHAR(64)  NOT NULL,
  msg_type     VARCHAR(32)  NOT NULL,   -- PAY_SUCCESS | REFUND_SUCCESS
  order_id     BIGINT       NOT NULL,
  template_id  VARCHAR(64)  NOT NULL,
  wx_msg_id    VARCHAR(64)  NULL,       -- 微信返回 msgid，成功时有值
  status       VARCHAR(32)  NOT NULL,   -- SENT | FAILED | SKIPPED_UNSUBSCRIBED | SKIPPED_NO_TEMPLATE_ID
  error_msg    TEXT         NULL,
  sent_at      DATETIME     NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_order (msg_type, order_id),
  KEY idx_openid (openid),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
