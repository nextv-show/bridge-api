-- admin 人工驳回原因落库。
-- kyc_records 由 h5-service(V007) 创建；admin-service 与 h5-service 生产共享同一库
-- （deploy 用 SPRING_DATASOURCE_URL 指向 h5_db），admin 以独立 flyway 历史表
-- (admin_flyway_schema_history) 在同库追加该列。h5-service 实体未映射此列，
-- ddl-auto=validate 只校验已映射列，新增列不影响 h5 启动。
ALTER TABLE kyc_records
  ADD COLUMN reject_reason VARCHAR(200) NULL COMMENT 'admin 人工驳回原因';
