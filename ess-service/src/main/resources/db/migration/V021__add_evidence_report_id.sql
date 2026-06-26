-- 出证报告（CreateFlowEvidenceReport）任务ID。
-- 腾讯电子签出证是两步异步：CreateFlowEvidenceReport 返回 ReportId（报告异步生成）→
-- DescribeFlowEvidenceReport(ReportId) 轮询直到 EvidenceStatusSuccess 拿 ReportUrl。
-- 需持久化 ReportId 以便后续查询；旧实现用的 CreateCertificate 是不存在的 action。
ALTER TABLE contracts
    ADD COLUMN evidence_report_id VARCHAR(64) NULL COMMENT '腾讯电子签出证报告任务ID(CreateFlowEvidenceReport 返回，供 DescribeFlowEvidenceReport 轮询)';
