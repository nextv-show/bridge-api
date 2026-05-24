package com.sanshuiyuan.admin.api;

import com.sanshuiyuan.admin.api.dto.RefundApproveRequest;
import com.sanshuiyuan.admin.api.dto.RefundRejectRequest;
import com.sanshuiyuan.admin.application.RefundReviewService;
import com.sanshuiyuan.admin.domain.RefundRecord;
import com.sanshuiyuan.admin.infra.repository.RefundRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 退款审核管理端 API
 */
@RestController
@RequestMapping("/admin/refunds")
public class RefundAdminController {

    private final RefundRecordRepository refundRepo;
    private final RefundReviewService refundReview;

    public RefundAdminController(RefundRecordRepository refundRepo,
                                  RefundReviewService refundReview) {
        this.refundRepo = refundRepo;
        this.refundReview = refundReview;
    }

    /** 退款列表（分页 + 状态/类型筛选） */
    @GetMapping("/records")
    public Page<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(name = "refundType", required = false) String refundType) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<RefundRecord> records;
        if (status != null && refundType != null) {
            records = refundRepo.findByStatusAndRefundType(
                    RefundRecord.Status.valueOf(status),
                    RefundRecord.RefundType.valueOf(refundType),
                    pageable);
        } else if (status != null) {
            records = refundRepo.findByStatus(RefundRecord.Status.valueOf(status), pageable);
        } else if (refundType != null) {
            records = refundRepo.findByRefundType(RefundRecord.RefundType.valueOf(refundType), pageable);
        } else {
            records = refundRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        return records.map(this::toDto);
    }

    /** 退款详情 */
    @GetMapping("/records/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        RefundRecord r = refundRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("退款记录不存在: " + id));
        return toDetailDto(r);
    }

    /** 审批通过 */
    @PostMapping("/records/{id}/approve")
    public Map<String, String> approve(@PathVariable Long id,
                                       @RequestBody(required = false) RefundApproveRequest body,
                                       Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        Long actualRefundCents = body != null ? body.getActualRefundCents() : null;
        refundReview.approve(adminId, id, actualRefundCents);
        return Map.of("status", "APPROVED");
    }

    /** 审批驳回 */
    @PostMapping("/records/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id,
                                       @RequestBody RefundRejectRequest body,
                                       Authentication auth) {
        Long adminId = (Long) auth.getPrincipal();
        refundReview.reject(adminId, id, body.getRejectReason());
        return Map.of("status", "REJECTED");
    }

    /** 各状态计数 */
    @GetMapping("/counts")
    public Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RefundRecord.Status s : RefundRecord.Status.values()) {
            result.put(s.name(), refundRepo.countByStatus(s));
        }
        return result;
    }

    /* ========== 列表简要 DTO ========== */
    private Map<String, Object> toDto(RefundRecord r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("orderId", r.getOrderId());
        dto.put("userId", r.getUserId());
        dto.put("refundNo", r.getRefundNo());
        dto.put("refundType", r.getRefundType() != null ? r.getRefundType().name() : "");
        dto.put("status", r.getStatus() != null ? r.getStatus().name() : "");
        dto.put("reasonCat", r.getReasonCat() != null ? r.getReasonCat() : "");
        dto.put("refundAmountCents", r.getRefundAmountCents());
        dto.put("paidAmountCents", r.getPaidAmountCents());
        dto.put("skuName", r.getSkuName() != null ? r.getSkuName() : "");
        dto.put("skuQty", r.getSkuQty());
        dto.put("realNameMask", r.getRealNameMask() != null ? r.getRealNameMask() : "");
        dto.put("phoneMask", r.getPhoneMask() != null ? r.getPhoneMask() : "");
        dto.put("riskLevel", r.getRiskLevel() != null ? r.getRiskLevel() : "low");
        dto.put("submittedAt", formatTime(r.getSubmittedAt()));
        dto.put("createdAt", formatTime(r.getCreatedAt()));
        return dto;
    }

    /* ========== 详情完整 DTO ========== */
    private Map<String, Object> toDetailDto(RefundRecord r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("orderId", r.getOrderId());
        dto.put("userId", r.getUserId());
        dto.put("refundNo", r.getRefundNo());
        dto.put("refundType", r.getRefundType() != null ? r.getRefundType().name() : "");
        dto.put("status", r.getStatus() != null ? r.getStatus().name() : "");
        dto.put("reasonCat", r.getReasonCat() != null ? r.getReasonCat() : "");
        dto.put("userMsg", r.getUserMsg() != null ? r.getUserMsg() : "");
        dto.put("rejectReason", r.getRejectReason() != null ? r.getRejectReason() : "");

        // 金额
        dto.put("orderAmountCents", r.getOrderAmountCents());
        dto.put("paidAmountCents", r.getPaidAmountCents());
        dto.put("refundAmountCents", r.getRefundAmountCents());
        dto.put("actualRefundCents", r.getActualRefundCents());
        dto.put("incomeDeductedCents", r.getIncomeDeductedCents());
        dto.put("feeCents", r.getFeeCents());

        // 支付
        dto.put("paymentChannel", r.getPaymentChannel() != null ? r.getPaymentChannel() : "");
        dto.put("paymentTxnId", r.getPaymentTxnId() != null ? r.getPaymentTxnId() : "");

        // 设备
        dto.put("deviceSn", r.getDeviceSn() != null ? r.getDeviceSn() : "");
        dto.put("deviceModel", r.getDeviceModel() != null ? r.getDeviceModel() : "");
        dto.put("deviceStage", r.getDeviceStage() != null ? r.getDeviceStage() : "");
        dto.put("installAddr", r.getInstallAddr() != null ? r.getInstallAddr() : "");

        // SKU
        dto.put("skuName", r.getSkuName() != null ? r.getSkuName() : "");
        dto.put("skuQty", r.getSkuQty());

        // 风险
        dto.put("riskLevel", r.getRiskLevel() != null ? r.getRiskLevel() : "low");

        // 脱敏
        dto.put("realNameMask", r.getRealNameMask() != null ? r.getRealNameMask() : "");
        dto.put("phoneMask", r.getPhoneMask() != null ? r.getPhoneMask() : "");
        dto.put("kycPassed", r.getKycPassed());

        // 时间
        dto.put("submittedAt", formatTime(r.getSubmittedAt()));
        dto.put("resolvedAt", formatTime(r.getResolvedAt()));
        dto.put("createdAt", formatTime(r.getCreatedAt()));

        // 审批人
        dto.put("operatorId", r.getOperatorId());
        dto.put("operatorName", r.getOperatorName() != null ? r.getOperatorName() : "");
        return dto;
    }

    private String formatTime(LocalDateTime t) {
        return t != null ? t.toString() : "";
    }
}
