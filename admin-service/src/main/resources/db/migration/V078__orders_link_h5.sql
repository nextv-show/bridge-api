-- Dual-write sync: link admin `orders` rows back to their source H5 order, and remove demo seed rows.
-- admin-service connects to h5_db (deploy 用 SPRING_DATASOURCE_URL 指向 h5_db)，以独立 flyway 历史表
-- (admin_flyway_schema_history) 在同库追加该列。cend-service 在同库内通过 JdbcTemplate
-- 按 h5_order_no 做 INSERT ... ON DUPLICATE KEY UPDATE 投影。
-- admin `Order` JPA 实体未映射 h5_order_no 列（ddl-auto: validate 容忍 DB 多余列），无需改实体。

ALTER TABLE orders ADD COLUMN h5_order_no VARCHAR(40) NULL;

-- MySQL 的唯一索引允许多个 NULL，因此现存（h5_order_no 为 NULL）的行不受影响。
CREATE UNIQUE INDEX uk_orders_h5_order_no ON orders(h5_order_no);

-- 移除后台演示用的种子订单（90001~90008）。
DELETE FROM orders WHERE id BETWEEN 90001 AND 90008;
