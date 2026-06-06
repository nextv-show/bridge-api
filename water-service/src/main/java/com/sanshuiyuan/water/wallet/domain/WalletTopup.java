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
 * 充值单。下单即 PENDING，微信回调验签通过后置 PAID 并落账。
 */
@Entity
@Table(name = "wallet_topups")
public class WalletTopup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "out_trade_no", nullable = false, unique = true, length = 64)
    private String outTradeNo;

    @Column(name = "wx_prepay_id", length = 64)
    private String wxPrepayId;

    @Column(name = "wx_transaction_id", unique = true, length = 64)
    private String wxTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TopupStatus status = TopupStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WalletTopup() {
    }

    public static WalletTopup create(Long userId, Long amountCents, String outTradeNo, String wxPrepayId) {
        WalletTopup t = new WalletTopup();
        t.userId = userId;
        t.amountCents = amountCents;
        t.outTradeNo = outTradeNo;
        t.wxPrepayId = wxPrepayId;
        t.status = TopupStatus.PENDING;
        return t;
    }

    /** 标记已支付：写入微信交易号、支付时间，状态置 PAID。 */
    public void markPaid(String wxTransactionId, LocalDateTime paidAt) {
        this.wxTransactionId = wxTransactionId;
        this.paidAt = paidAt;
        this.status = TopupStatus.PAID;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAmountCents() { return amountCents; }
    public String getOutTradeNo() { return outTradeNo; }
    public String getWxPrepayId() { return wxPrepayId; }
    public String getWxTransactionId() { return wxTransactionId; }
    public TopupStatus getStatus() { return status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
