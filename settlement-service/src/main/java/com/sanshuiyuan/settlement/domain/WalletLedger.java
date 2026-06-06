package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 钱包流水：每次余额变动的不可变记账记录。 */
@Entity
@Table(name = "wallet_ledger")
public class WalletLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private LedgerSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private Long balanceAfterCents;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WalletLedger() {}

    public WalletLedger(Long userId, LedgerDirection direction, LedgerSourceType sourceType, Long sourceId,
                        Long amountCents, Long balanceAfterCents) {
        this.userId = userId;
        this.direction = direction;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LedgerDirection getDirection() { return direction; }
    public void setDirection(LedgerDirection direction) { this.direction = direction; }
    public LedgerSourceType getSourceType() { return sourceType; }
    public void setSourceType(LedgerSourceType sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public Long getBalanceAfterCents() { return balanceAfterCents; }
    public void setBalanceAfterCents(Long balanceAfterCents) { this.balanceAfterCents = balanceAfterCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
