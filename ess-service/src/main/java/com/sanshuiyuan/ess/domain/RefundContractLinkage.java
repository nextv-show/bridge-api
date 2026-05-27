package com.sanshuiyuan.ess.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 退款合同联动实体。
 * <p>
 * 冷静期/补充协议/退款三方状态联动。
 */
@Entity
@Table(name = "refund_contract_linkages")
public class RefundContractLinkage {

    /**
     * 联动状态枚举。
     */
    public enum LinkageStatus {
        /** 待处理（补充协议未签） */
        PENDING,
        /** 补充协议已签署，等待退款审批 */
        SUPPLEMENTARY_SIGNED,
        /** 退款已审批 */
        REFUND_APPROVED,
        /** 退款已完成 */
        REFUND_COMPLETED,
        /** 已取消 */
        CANCELLED;

        public boolean canTransitionTo(LinkageStatus target) {
            return switch (this) {
                case PENDING -> target == SUPPLEMENTARY_SIGNED || target == CANCELLED;
                case SUPPLEMENTARY_SIGNED -> target == REFUND_APPROVED || target == CANCELLED;
                case REFUND_APPROVED -> target == REFUND_COMPLETED || target == CANCELLED;
                case REFUND_COMPLETED, CANCELLED -> false;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplementary_contract_id", nullable = false)
    private Long supplementaryContractId;

    @Column(name = "refund_order_id", nullable = false, length = 64)
    private String refundOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "linkage_status", nullable = false, length = 32)
    private LinkageStatus linkageStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected RefundContractLinkage() {
    }

    /**
     * 创建联动记录。
     */
    public static RefundContractLinkage create(Long supplementaryContractId, String refundOrderId) {
        RefundContractLinkage linkage = new RefundContractLinkage();
        linkage.supplementaryContractId = supplementaryContractId;
        linkage.refundOrderId = refundOrderId;
        linkage.linkageStatus = LinkageStatus.PENDING;
        return linkage;
    }

    /**
     * 标记补充协议已签署。
     */
    public void markSupplementarySigned() {
        validateTransition(LinkageStatus.SUPPLEMENTARY_SIGNED);
        this.linkageStatus = LinkageStatus.SUPPLEMENTARY_SIGNED;
    }

    /**
     * 标记退款已审批。
     */
    public void markRefundApproved() {
        validateTransition(LinkageStatus.REFUND_APPROVED);
        this.linkageStatus = LinkageStatus.REFUND_APPROVED;
    }

    /**
     * 标记退款已完成。
     */
    public void markRefundCompleted() {
        validateTransition(LinkageStatus.REFUND_COMPLETED);
        this.linkageStatus = LinkageStatus.REFUND_COMPLETED;
    }

    /**
     * 取消。
     */
    public void cancel() {
        if (!this.linkageStatus.canTransitionTo(LinkageStatus.CANCELLED)) {
            return; // 幂等
        }
        this.linkageStatus = LinkageStatus.CANCELLED;
    }

    private void validateTransition(LinkageStatus target) {
        if (!this.linkageStatus.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("联动状态不允许从 %s 转换到 %s [refundOrderId=%s]",
                            this.linkageStatus, target, this.refundOrderId));
        }
    }

    public Long getId() { return id; }
    public Long getSupplementaryContractId() { return supplementaryContractId; }
    public String getRefundOrderId() { return refundOrderId; }
    public LinkageStatus getLinkageStatus() { return linkageStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
