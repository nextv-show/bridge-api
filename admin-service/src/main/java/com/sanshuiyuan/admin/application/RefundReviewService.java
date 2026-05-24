package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.AdminUser;
import com.sanshuiyuan.admin.domain.RefundRecord;
import com.sanshuiyuan.admin.infra.repository.AdminUserRepository;
import com.sanshuiyuan.admin.infra.repository.RefundRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

/**
 * 退款审核服务 — 处理审批通过 / 驳回逻辑
 */
@Service
public class RefundReviewService {

    private final RefundRecordRepository refundRepo;
    private final AdminUserRepository adminUserRepo;
    private final AuditLogService auditLog;

    public RefundReviewService(RefundRecordRepository refundRepo,
                               AdminUserRepository adminUserRepo,
                               AuditLogService auditLog) {
        this.refundRepo = refundRepo;
        this.adminUserRepo = adminUserRepo;
        this.auditLog = auditLog;
    }

    /** 审批通过 — PENDING → APPROVED */
    @Transactional
    public void approve(Long adminId, Long refundId, Long actualRefundCents) {
        var record = refundRepo.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("退款记录不存在: " + refundId));

        // 状态校验在 entity 的 approve 方法中完成
        String operatorName = resolveOperatorName(adminId);
        record.approve(adminId, operatorName, actualRefundCents);
        refundRepo.save(record);

        // 审计日志
        String payload = actualRefundCents != null
                ? "{\"actualRefundCents\":" + actualRefundCents + "}"
                : null;
        auditLog.log(adminId, "REFUND_APPROVE", "refund_record",
                String.valueOf(refundId), payload);
    }

    /** 审批驳回 — PENDING → REJECTED */
    @Transactional
    public void reject(Long adminId, Long refundId, String rejectReason) {
        var record = refundRepo.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("退款记录不存在: " + refundId));

        // 状态校验在 entity 的 reject 方法中完成
        String operatorName = resolveOperatorName(adminId);
        record.reject(adminId, operatorName, rejectReason);
        refundRepo.save(record);

        // 审计日志
        String payload = rejectReason != null
                ? "{\"rejectReason\":\"" + escapeJson(rejectReason) + "\"}"
                : null;
        auditLog.log(adminId, "REFUND_REJECT", "refund_record",
                String.valueOf(refundId), payload);
    }

    private String resolveOperatorName(Long adminId) {
        return adminUserRepo.findById(adminId)
                .map(AdminUser::getUsername)
                .orElse("unknown");
    }

    /** 简易 JSON 字符串转义 */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
