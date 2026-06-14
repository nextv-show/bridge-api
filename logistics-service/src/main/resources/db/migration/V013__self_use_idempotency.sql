-- 027: SELF_USE 幂等 — logistics_orders 增加 outbox_id 列
ALTER TABLE logistics_orders ADD COLUMN outbox_id BIGINT NULL;
ALTER TABLE logistics_orders ADD UNIQUE INDEX uk_outbox (outbox_id);
