-- V020 冷静期返利冻结台账（011-rebate-freeze）。
-- 支付成功后，按下单时刻订单快照的 inviter_id(L1) / grand_inviter_id(L2) 各冻结一条返利记录。
-- 合规铁律：
--   1. 24h 冷静期内返利为 FROZEN（冻结/待确认），不对外展示具体金额；
--   2. 冷静期满 FROZEN→CONFIRMED（确认），方可展示金额；
--   3. 退款 = 实体商品买卖合同解除：冷静期内 FROZEN→CANCELLED(REFUND_COOLDOWN)，
--      冷静期后 CONFIRMED→CANCELLED(REFUND_POST_COOLDOWN)；
--   4. 状态单向流转，无逆向；
--   5. 仅 L1+L2 两级，严禁 L3+ 受益记录（level 取值仅 'L1'/'L2'）。
--
-- 列类型遵循 V013 既定约定：实体以 @Enumerated(STRING) 映射枚举列，ddl-auto=validate 期望 VARCHAR，
-- 故 level/status 用 VARCHAR 而非 MySQL ENUM（避免全新库启动校验失败）。
-- beneficiary_id 为受益人 H5 user_id（= h5_users.id，订单快照列存的就是该 id）。
CREATE TABLE pending_rebates (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id       BIGINT NOT NULL COMMENT '触发返利的订单 id（h5_orders.id）',
  beneficiary_id BIGINT NOT NULL COMMENT '受益人 H5 user_id（L1=订单 inviter_id，L2=订单 grand_inviter_id）',
  level          VARCHAR(2) NOT NULL COMMENT '受益层级，仅 L1/L2（严禁 L3+）',
  amount_cents   BIGINT NOT NULL DEFAULT 0 COMMENT '返利金额（分）；分账算法待运营确认，暂为配置占位值',
  status         VARCHAR(16) NOT NULL COMMENT '状态：FROZEN/CONFIRMED/CANCELLED，单向流转',
  frozen_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '冻结时刻（= 支付成功时刻）',
  confirmed_at   DATETIME NULL COMMENT '确认时刻（冷静期满解冻）',
  cancelled_at   DATETIME NULL COMMENT '取消时刻（退款触发）',
  cancel_reason  VARCHAR(32) NULL COMMENT '取消原因：REFUND_COOLDOWN/REFUND_POST_COOLDOWN',
  -- 幂等：同一订单同一受益人同一层级至多一条，防重复回调重复冻结。
  UNIQUE KEY uk_order_beneficiary_level (order_id, beneficiary_id, level),
  KEY idx_beneficiary_status (beneficiary_id, status),
  KEY idx_order (order_id)
);
