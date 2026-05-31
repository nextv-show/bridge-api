-- V009 推荐返利冻结台账 —— 一次性实物销售介绍费（asset_db）。
-- 业务定性：推荐返利 = 一次性「实物销售介绍费」，由被推荐人「购机订单支付成功」触发，
--           金额按机型（SKU）配置的固定金额（severely NOT 购机款百分比！），L1 一档、L2 一档。
-- 合规铁律：
--   1. 每个被推荐人「仅一次」：首个合格购机订单触发，之后再下单不再产生返利
--      （封顶键 = uk_referee_level，按被推荐人 + 层级唯一，而非按订单）；
--   2. 24h 冷静期内返利为 FROZEN（冻结/待确认），不对外展示具体金额；
--   3. 冷静期满 FROZEN→CONFIRMED（确认），方可展示金额；
--   4. 退款 = 实体商品买卖合同解除：冷静期内 FROZEN→CANCELLED(REFUND_COOLDOWN)，
--      冷静期后 CONFIRMED→CANCELLED(REFUND_POST_COOLDOWN)；
--   5. 状态单向流转，无逆向；
--   6. 仅 L1+L2 两级，严禁 L3+ 受益记录（level 取值仅 'L1'/'L2'）。
--
-- 列类型遵循既有约定：实体以 @Enumerated(STRING) 映射枚举列，ddl-auto=validate 期望 VARCHAR，
-- 故 level/status/cancel_reason 用 VARCHAR 而非 MySQL ENUM。
-- beneficiary_id = 受益人 user_id（L1=被推荐人的直接邀请人，L2=间接邀请人）。
-- referee_id     = 被推荐人 = 购机下单人（orders.user_id），用于「每人仅一次」封顶约束。
CREATE TABLE pending_rebates (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id       BIGINT NOT NULL COMMENT '触发返利的购机订单 id（orders.id），记录触发来源',
  referee_id     BIGINT NOT NULL COMMENT '被推荐人 user_id（= 购机下单人 orders.user_id），按人封顶一次的主体',
  beneficiary_id BIGINT NOT NULL COMMENT '受益人 user_id（L1=被推荐人的直接邀请人，L2=间接邀请人）',
  level          VARCHAR(2) NOT NULL COMMENT '受益层级，仅 L1/L2（严禁 L3+）',
  amount_cents   BIGINT NOT NULL DEFAULT 0 COMMENT '介绍费金额（分）；下单时刻按 SKU 固定费率快照，之后改费率不影响本记录',
  status         VARCHAR(16) NOT NULL COMMENT '状态：FROZEN/CONFIRMED/CANCELLED，单向流转',
  frozen_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '冻结时刻（= 支付成功时刻）',
  confirmed_at   DATETIME NULL COMMENT '确认时刻（冷静期满解冻）',
  cancelled_at   DATETIME NULL COMMENT '取消时刻（退款触发）',
  cancel_reason  VARCHAR(32) NULL COMMENT '取消原因：REFUND_COOLDOWN/REFUND_POST_COOLDOWN',
  -- 按人封顶一次：同一被推荐人对同一层级至多一条返利记录，无论其下几单（合规关键修正）。
  UNIQUE KEY uk_referee_level (referee_id, level),
  KEY idx_beneficiary_status (beneficiary_id, status),
  KEY idx_order (order_id)
);
