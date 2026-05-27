-- V009 扩展 contracts 表，添加出证相关字段
-- 签署+归档完成后自动向腾讯电子签申请出证

ALTER TABLE contracts
    ADD COLUMN certificate_status  VARCHAR(32)  NULL     COMMENT '出证状态: PENDING/APPLYING/CERTIFIED/FAILED' AFTER certificate_no,
    ADD COLUMN certificate_pdf_url VARCHAR(512) NULL     COMMENT '出证 PDF 下载地址' AFTER certificate_status,
    ADD COLUMN certified_at        TIMESTAMP    NULL     COMMENT '出证完成时间' AFTER certificate_pdf_url,
    ADD INDEX idx_certificate_status (certificate_status);
