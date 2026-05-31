package com.sanshuiyuan.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 购机订单退款记录（冷静期无理由退款，实体商品买卖合同解除）。
 * 一订单一退款（order_id 唯一）；refund_no = 商户侧退款单号（out_refund_no），全局唯一。
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "refund_no", nullable = false, unique = true, length = 64)
    private String refundNo;

    @Column(name = "wx_refund_id", length = 64)
    private String wxRefundId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "succeeded_at")
    private LocalDateTime succeededAt;

    protected Refund() {
    }

    public static Refund create(Long orderId, String refundNo, Long amountCents) {
        Refund r = new Refund();
        r.orderId = orderId;
        r.refundNo = refundNo;
        r.amountCents = amountCents;
        r.status = RefundStatus.PROCESSING;
        return r;
    }

    public void markSuccess(String wxRefundId) {
        this.status = RefundStatus.SUCCESS;
        this.wxRefundId = wxRefundId;
        this.succeededAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = RefundStatus.FAILED;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getRefundNo() { return refundNo; }
    public String getWxRefundId() { return wxRefundId; }
    public Long getAmountCents() { return amountCents; }
    public RefundStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSucceededAt() { return succeededAt; }
}
