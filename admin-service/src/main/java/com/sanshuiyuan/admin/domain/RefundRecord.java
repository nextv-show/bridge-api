package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 退款记录实体 — 对应 asset_db.refund_records 表。
 * 状态流转: PENDING → APPROVED → REFUNDING → REFUNDED
 *                 └→ REJECTED
 *                 └→ CLOSED
 */
@Entity
@Table(name = "refund_records")
public class RefundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "refund_no", nullable = false, length = 64, unique = true)
    private String refundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", nullable = false, length = 16)
    private RefundType refundType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "reason_cat", nullable = false, length = 32)
    private String reasonCat;

    @Column(name = "user_msg", columnDefinition = "TEXT")
    private String userMsg;

    @Column(name = "reject_reason", length = 256)
    private String rejectReason;

    /* ---- 金额（cents） ---- */
    @Column(name = "order_amount_cents", nullable = false)
    private Long orderAmountCents;

    @Column(name = "paid_amount_cents", nullable = false)
    private Long paidAmountCents;

    @Column(name = "refund_amount_cents", nullable = false)
    private Long refundAmountCents;

    @Column(name = "actual_refund_cents")
    private Long actualRefundCents;

    @Column(name = "income_deducted_cents", nullable = false)
    private Long incomeDeductedCents = 0L;

    @Column(name = "fee_cents", nullable = false)
    private Long feeCents = 0L;

    /* ---- 支付信息 ---- */
    @Column(name = "payment_channel", length = 16)
    private String paymentChannel;

    @Column(name = "payment_txn_id", length = 64)
    private String paymentTxnId;

    /* ---- 设备信息快照 ---- */
    @Column(name = "device_sn", length = 64)
    private String deviceSn;

    @Column(name = "device_model", length = 128)
    private String deviceModel;

    @Column(name = "device_stage", length = 32)
    private String deviceStage;

    @Column(name = "install_addr", length = 256)
    private String installAddr;

    /* ---- SKU 信息 ---- */
    @Column(name = "sku_name", length = 128)
    private String skuName;

    @Column(name = "sku_qty", nullable = false)
    private Integer skuQty = 1;

    /* ---- 风险等级 ---- */
    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "low";

    /* ---- 脱敏用户信息 ---- */
    @Column(name = "real_name_mask", length = 32)
    private String realNameMask;

    @Column(name = "phone_mask", length = 32)
    private String phoneMask;

    @Column(name = "kyc_passed", nullable = false)
    private Boolean kycPassed = false;

    /* ---- 时间 ---- */
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /* ---- 审批人 ---- */
    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    /* ========== 枚举 ========== */

    public enum Status {
        PENDING,    // 待审核
        APPROVED,   // 已通过
        REFUNDING,  // 退款中
        REFUNDED,   // 已退款
        REJECTED,   // 已驳回
        CLOSED      // 已关闭
    }

    public enum RefundType {
        FULL,       // 全额退款
        PARTIAL     // 部分退款
    }

    /* ========== 状态变更方法 ========== */

    /** 审批通过 — PENDING → APPROVED */
    public void approve(Long operatorId, String operatorName, Long actualRefundCents) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("退款状态不可审批通过: " + this.status);
        }
        this.status = Status.APPROVED;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.actualRefundCents = actualRefundCents != null ? actualRefundCents : this.refundAmountCents;
        this.resolvedAt = LocalDateTime.now();
    }

    /** 审批驳回 — PENDING → REJECTED */
    public void reject(Long operatorId, String operatorName, String rejectReason) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("退款状态不可驳回: " + this.status);
        }
        this.status = Status.REJECTED;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.rejectReason = rejectReason;
        this.resolvedAt = LocalDateTime.now();
    }

    /* ========== getter / setter ========== */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }

    public RefundType getRefundType() { return refundType; }
    public void setRefundType(RefundType refundType) { this.refundType = refundType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getReasonCat() { return reasonCat; }
    public void setReasonCat(String reasonCat) { this.reasonCat = reasonCat; }

    public String getUserMsg() { return userMsg; }
    public void setUserMsg(String userMsg) { this.userMsg = userMsg; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Long getOrderAmountCents() { return orderAmountCents; }
    public void setOrderAmountCents(Long orderAmountCents) { this.orderAmountCents = orderAmountCents; }

    public Long getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Long paidAmountCents) { this.paidAmountCents = paidAmountCents; }

    public Long getRefundAmountCents() { return refundAmountCents; }
    public void setRefundAmountCents(Long refundAmountCents) { this.refundAmountCents = refundAmountCents; }

    public Long getActualRefundCents() { return actualRefundCents; }
    public void setActualRefundCents(Long actualRefundCents) { this.actualRefundCents = actualRefundCents; }

    public Long getIncomeDeductedCents() { return incomeDeductedCents; }
    public void setIncomeDeductedCents(Long incomeDeductedCents) { this.incomeDeductedCents = incomeDeductedCents; }

    public Long getFeeCents() { return feeCents; }
    public void setFeeCents(Long feeCents) { this.feeCents = feeCents; }

    public String getPaymentChannel() { return paymentChannel; }
    public void setPaymentChannel(String paymentChannel) { this.paymentChannel = paymentChannel; }

    public String getPaymentTxnId() { return paymentTxnId; }
    public void setPaymentTxnId(String paymentTxnId) { this.paymentTxnId = paymentTxnId; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getDeviceStage() { return deviceStage; }
    public void setDeviceStage(String deviceStage) { this.deviceStage = deviceStage; }

    public String getInstallAddr() { return installAddr; }
    public void setInstallAddr(String installAddr) { this.installAddr = installAddr; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public Integer getSkuQty() { return skuQty; }
    public void setSkuQty(Integer skuQty) { this.skuQty = skuQty; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRealNameMask() { return realNameMask; }
    public void setRealNameMask(String realNameMask) { this.realNameMask = realNameMask; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }

    public Boolean getKycPassed() { return kycPassed; }
    public void setKycPassed(Boolean kycPassed) { this.kycPassed = kycPassed; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
}
