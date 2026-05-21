package com.sanshuiyuan.h5.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 128)
    private String reason;

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
        r.reason = "冷静期无理由退款";
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
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSucceededAt() { return succeededAt; }
}
