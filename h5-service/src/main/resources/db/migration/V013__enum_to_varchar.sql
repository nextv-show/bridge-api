-- 实体以 @Enumerated(STRING) 映射这些状态/渠道列，Hibernate `ddl-auto=validate`
-- 期望 VARCHAR，但 V005~V012 把它们建成了 MySQL ENUM，导致全新库启动校验失败。
-- 这里前向转为 VARCHAR（长度对齐实体 @Column(length)），ENUM→VARCHAR 保留既有字符串值，prod 平滑。
ALTER TABLE h5_orders
  MODIFY COLUMN status          VARCHAR(20) NOT NULL,
  MODIFY COLUMN payment_channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT';

ALTER TABLE refunds
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING';

ALTER TABLE invoices
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ISSUING';

ALTER TABLE device_specs
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE kyc_records
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'INIT';
