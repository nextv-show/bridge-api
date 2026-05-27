-- V007 扩展 contracts 表，添加归档相关字段
-- 合同签署完成后双冗余存储（腾讯云端 + 自有 OSS）、哈希审计、下载计数

ALTER TABLE contracts
    ADD COLUMN tencent_cloud_url  VARCHAR(512) NULL     COMMENT '腾讯云端存储 URL' AFTER pdf_hash,
    ADD COLUMN oss_url            VARCHAR(512) NULL     COMMENT '自有 OSS 存储 URL' AFTER tencent_cloud_url,
    ADD COLUMN archive_status     VARCHAR(32)  NULL     COMMENT '归档状态: PENDING/ARCHIVING/ARCHIVED/FAILED' AFTER oss_url,
    ADD COLUMN certificate_no     VARCHAR(128) NULL     COMMENT '出证编号（预留）' AFTER archive_status,
    ADD COLUMN download_count     INT          NOT NULL DEFAULT 0 COMMENT '下载次数' AFTER certificate_no,
    ADD COLUMN archived_at        TIMESTAMP    NULL     COMMENT '归档完成时间' AFTER download_count,
    ADD INDEX idx_archive_status (archive_status);
