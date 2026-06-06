package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 提现分账：提现单到具体支付渠道的代付明细。 */
@Entity
@Table(name = "withdrawal_splits")
public class WithdrawalSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitKind kind;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentChannel channel;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(nullable = false)
    private Integer retried;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    protected WithdrawalSplit() {}

    public WithdrawalSplit(Long orderId, SplitKind kind, Long amountCents, PaymentChannel channel,
                           SplitStatus status, Integer retried) {
        this.orderId = orderId;
        this.kind = kind;
        this.amountCents = amountCents;
        this.channel = channel;
        this.status = status;
        this.retried = retried;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public SplitKind getKind() { return kind; }
    public void setKind(SplitKind kind) { this.kind = kind; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public PaymentChannel getChannel() { return channel; }
    public void setChannel(PaymentChannel channel) { this.channel = channel; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public SplitStatus getStatus() { return status; }
    public void setStatus(SplitStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public Integer getRetried() { return retried; }
    public void setRetried(Integer retried) { this.retried = retried; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
}
