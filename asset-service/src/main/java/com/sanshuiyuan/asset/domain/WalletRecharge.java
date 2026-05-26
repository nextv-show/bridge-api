package com.sanshuiyuan.asset.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 水费充值单（预收账款）。create → PENDING_PAY，支付成功回调 → markPaid（入账钱包）。
 */
@Entity
@Table(name = "wallet_recharge")
public class WalletRecharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amountCents;

    @Column(nullable = false)
    private Integer pointsGranted = 0;

    @Column(nullable = false)
    private Integer litersGranted = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RechargeStatus status;

    @Column(length = 24)
    private String payChannel;

    @Column(length = 64)
    private String wxTransactionId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime paidAt;

    public static WalletRecharge create(Long userId, long amountCents, int points, int liters, String payChannel) {
        WalletRecharge r = new WalletRecharge();
        r.userId = userId;
        r.amountCents = amountCents;
        r.pointsGranted = points;
        r.litersGranted = liters;
        r.status = RechargeStatus.PENDING_PAY;
        r.payChannel = payChannel;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    public void markPaid(String txnId) {
        this.status = RechargeStatus.PAID;
        this.wxTransactionId = txnId;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAmountCents() { return amountCents; }
    public Integer getPointsGranted() { return pointsGranted; }
    public Integer getLitersGranted() { return litersGranted; }
    public RechargeStatus getStatus() { return status; }
    public String getPayChannel() { return payChannel; }
    public String getWxTransactionId() { return wxTransactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
