package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 提现单：用户发起的一次提现，gross = fee + cash。 */
@Entity
@Table(name = "withdrawal_orders")
public class WithdrawalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "gross_cents", nullable = false)
    private Long grossCents;

    @Column(name = "fee_cents", nullable = false)
    private Long feeCents;

    @Column(name = "cash_cents", nullable = false)
    private Long cashCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "client_request_id")
    private String clientRequestId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected WithdrawalOrder() {}

    public WithdrawalOrder(Long userId, Long grossCents, Long feeCents, Long cashCents,
                           WithdrawalStatus status, String clientRequestId) {
        this.userId = userId;
        this.grossCents = grossCents;
        this.feeCents = feeCents;
        this.cashCents = cashCents;
        this.status = status;
        this.clientRequestId = clientRequestId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getGrossCents() { return grossCents; }
    public void setGrossCents(Long grossCents) { this.grossCents = grossCents; }
    public Long getFeeCents() { return feeCents; }
    public void setFeeCents(Long feeCents) { this.feeCents = feeCents; }
    public Long getCashCents() { return cashCents; }
    public void setCashCents(Long cashCents) { this.cashCents = cashCents; }
    public WithdrawalStatus getStatus() { return status; }
    public void setStatus(WithdrawalStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
