-- 027-self-use-path：logistics_orders.request_id 允许 NULL（自用场景无匹配需求）
ALTER TABLE logistics_orders
  MODIFY COLUMN request_id BIGINT NULL;
