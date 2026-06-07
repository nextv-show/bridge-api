package com.sanshuiyuan.water.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * C 端钱包余额（每用户一行）。乐观锁 {@link Version} 防并发覆盖；余额单位为分（非负，DB CHECK 约束）。
 */
@Entity
@Table(name = "consumer_wallets")
public class ConsumerWallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ConsumerWallet() {
    }

    public static ConsumerWallet create(Long userId, Long balanceCents) {
        ConsumerWallet w = new ConsumerWallet();
        w.userId = userId;
        w.balanceCents = balanceCents;
        return w;
    }

    /** 入账：余额累加。 */
    public void credit(Long amountCents) {
        this.balanceCents += amountCents;
    }

    public Long getUserId() { return userId; }
    public Long getBalanceCents() { return balanceCents; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
