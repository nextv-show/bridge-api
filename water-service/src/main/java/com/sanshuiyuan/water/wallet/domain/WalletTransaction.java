package com.sanshuiyuan.water.wallet.domain;

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
 * 钱包流水（不可变记账行）。唯一键 (source_type, source_id, direction) 保证同一来源只记一次账。
 */
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private Long balanceAfterCents;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WalletTransaction() {
    }

    public static WalletTransaction of(Long userId, Direction direction, SourceType sourceType,
                                       Long sourceId, Long amountCents, Long balanceAfterCents) {
        WalletTransaction t = new WalletTransaction();
        t.userId = userId;
        t.direction = direction;
        t.sourceType = sourceType;
        t.sourceId = sourceId;
        t.amountCents = amountCents;
        t.balanceAfterCents = balanceAfterCents;
        return t;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Direction getDirection() { return direction; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceId() { return sourceId; }
    public Long getAmountCents() { return amountCents; }
    public Long getBalanceAfterCents() { return balanceAfterCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
