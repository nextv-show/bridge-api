package com.sanshuiyuan.asset.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 消费者水费钱包：预收账款余额（cents）+ 健康积分 + 水量配额。
 * 主键即 userId（一个用户一个钱包）。资金仅用于水费消费，不计利息、不可提现。
 */
@Entity
@Table(name = "consumer_wallet")
public class ConsumerWallet {

    @Id
    private Long userId;

    @Column(nullable = false)
    private Long balanceCents = 0L;

    @Column(nullable = false)
    private Integer points = 0;

    @Column(nullable = false)
    private Integer litersQuota = 0;

    @Column(nullable = false)
    private Long dailyAvgCents = 0L;

    @Column
    private Long lastRechargeCents;

    @Column
    private LocalDateTime lastRechargeAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ConsumerWallet createFor(Long userId) {
        ConsumerWallet w = new ConsumerWallet();
        w.userId = userId;
        LocalDateTime now = LocalDateTime.now();
        w.createdAt = now;
        w.updatedAt = now;
        return w;
    }

    /** 入账一笔充值：余额 + 积分 + 配额，并记录最近一次充值。 */
    public void credit(long amountCents, int pointsGranted, int litersGranted) {
        this.balanceCents += amountCents;
        this.points += pointsGranted;
        this.litersQuota += litersGranted;
        this.lastRechargeCents = amountCents;
        this.lastRechargeAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBalanceCents() { return balanceCents; }
    public void setBalanceCents(Long balanceCents) { this.balanceCents = balanceCents; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getLitersQuota() { return litersQuota; }
    public void setLitersQuota(Integer litersQuota) { this.litersQuota = litersQuota; }
    public Long getDailyAvgCents() { return dailyAvgCents; }
    public void setDailyAvgCents(Long dailyAvgCents) { this.dailyAvgCents = dailyAvgCents; }
    public Long getLastRechargeCents() { return lastRechargeCents; }
    public void setLastRechargeCents(Long lastRechargeCents) { this.lastRechargeCents = lastRechargeCents; }
    public LocalDateTime getLastRechargeAt() { return lastRechargeAt; }
    public void setLastRechargeAt(LocalDateTime lastRechargeAt) { this.lastRechargeAt = lastRechargeAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
