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
    public void reject(Long adminId, Long kycRecordId) {
        var record = kycRepo.findById(kycRecordId)
                .orElseThrow(() -> new IllegalArgumentException("KYC 记录不存在: " + kycRecordId));
        if (record.getStatus() != KycRecord.Status.PENDING
                && record.getStatus() != KycRecord.Status.INIT) {
            throw new IllegalStateException("KYC 状态不可审核: " + record.getStatus());
        }
        record.reject();
        kycRepo.save(record);
        auditLog.log(adminId, "KYC_REJECT", "kyc_record",
                String.valueOf(kycRecordId), null);
    }
}
