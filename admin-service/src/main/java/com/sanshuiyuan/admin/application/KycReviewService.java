package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.KycRecord;
import com.sanshuiyuan.admin.infra.repository.KycRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class KycReviewService {

    private final KycRecordRepository kycRepo;
    private final AuditLogService auditLog;

    public KycReviewService(KycRecordRepository kycRepo, AuditLogService auditLog) {
        this.kycRepo = kycRepo;
        this.auditLog = auditLog;
    }

    @Transactional
    public void approve(Long adminId, Long kycRecordId) {
        var record = kycRepo.findById(kycRecordId)
                .orElseThrow(() -> new IllegalArgumentException("KYC 记录不存在: " + kycRecordId));
        if (record.getStatus() != KycRecord.Status.PENDING
                && record.getStatus() != KycRecord.Status.INIT) {
            throw new IllegalStateException("KYC 状态不可审核: " + record.getStatus());
        }
        record.approve();
        kycRepo.save(record);
        auditLog.log(adminId, "KYC_APPROVE", "kyc_record",
                String.valueOf(kycRecordId), null);
    }

    @Transactional
    public void reject(Long adminId, Long kycRecordId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("驳回原因不能为空");
        }
        String trimmed = reason.strip();
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200);
        }
        var record = kycRepo.findById(kycRecordId)
                .orElseThrow(() -> new IllegalArgumentException("KYC 记录不存在: " + kycRecordId));
        if (record.getStatus() != KycRecord.Status.PENDING
                && record.getStatus() != KycRecord.Status.INIT) {
            throw new IllegalStateException("KYC 状态不可审核: " + record.getStatus());
        }
        record.reject(trimmed);
        kycRepo.save(record);
        auditLog.log(adminId, "KYC_REJECT", "kyc_record",
                String.valueOf(kycRecordId),
                "{\"reason\":" + jsonString(trimmed) + "}");
    }

    /** 最小 JSON 字符串转义，避免驳回原因中的引号/反斜杠破坏审计 payload。 */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
