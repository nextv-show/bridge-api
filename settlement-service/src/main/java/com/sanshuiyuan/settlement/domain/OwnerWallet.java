package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 所有权人钱包：可用余额 + 冻结余额，乐观锁版本号。 */
@Entity
@Table(name = "owner_wallets")
public class OwnerWallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents;

    @Column(name = "frozen_cents", nullable = false)
    private Long frozenCents;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected OwnerWallet() {}

    public OwnerWallet(Long userId, Long balanceCents, Long frozenCents) {
        this.userId = userId;
        this.balanceCents = balanceCents;
        this.frozenCents = frozenCents;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBalanceCents() { return balanceCents; }
    public void setBalanceCents(Long balanceCents) { this.balanceCents = balanceCents; }
    public Long getFrozenCents() { return frozenCents; }
    public void setFrozenCents(Long frozenCents) { this.frozenCents = frozenCents; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
