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
@Table(name = "h5_orders")
public class H5Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "spec_id", nullable = false, length = 32)
    private String specId;

    @Column(name = "model_code", nullable = false, length = 32)
    private String modelCode;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "payment_channel", nullable = false, length = 16)
    private String paymentChannel;

    @Column(name = "wx_prepay_id", length = 64)
    private String wxPrepayId;

    @Column(name = "wx_transaction_id", length = 64)
    private String wxTransactionId;

    @Column(length = 64)
    private String sn;

    @Column(name = "cooldown_end_at")
    private LocalDateTime cooldownEndAt;

    /** 下单时刻快照：L1 邀请人 user_id（自然流量为 null）。绑定快照逻辑见 008b。 */
    @Column(name = "inviter_id")
    private Long inviterId;

    /** 下单时刻快照：L2 间接邀请人 user_id（可 null）。仅快照存储，严禁向上递归查询（L3+ 物理隔离）。 */
    @Column(name = "grand_inviter_id")
    private Long grandInviterId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected H5Order() {
    }

    public static H5Order create(String orderNo, String openid, String specId,
                                  String modelCode, Long amountCents, String paymentChannel) {
        H5Order o = new H5Order();
        o.orderNo = orderNo;
        o.openid = openid;
        o.specId = specId;
        o.modelCode = modelCode;
        o.amountCents = amountCents;
        o.paymentChannel = paymentChannel;
        o.status = OrderStatus.PENDING_PAY;
        return o;
    }

    public void markPaid(String wxTransactionId, String sn, LocalDateTime cooldownEndAt) {
        this.status = OrderStatus.PAID;
        this.wxTransactionId = wxTransactionId;
        this.sn = sn;
        this.paidAt = LocalDateTime.now();
        this.cooldownEndAt = cooldownEndAt;
    }

    public void close() {
        this.status = OrderStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    public void markRefunding() {
        this.status = OrderStatus.REFUNDING;
    }

    public void markRefunded() {
        this.status = OrderStatus.REFUNDED;
    }

    public void revertToPaid() {
        this.status = OrderStatus.PAID;
    }

    public void setWxPrepayId(String wxPrepayId) { this.wxPrepayId = wxPrepayId; }

    /**
     * 下单时刻快照当前用户的关系链（L1/L2）。仅一次性写入，不做向上递归追溯。
     * 绑定/调用时机见 008b。
     */
    public void snapshotReferral(Long inviterId, Long grandInviterId) {
        this.inviterId = inviterId;
        this.grandInviterId = grandInviterId;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getOpenid() { return openid; }
    public String getSpecId() { return specId; }
    public String getModelCode() { return modelCode; }
    public Long getAmountCents() { return amountCents; }
    public OrderStatus getStatus() { return status; }
    public String getPaymentChannel() { return paymentChannel; }
    public String getWxPrepayId() { return wxPrepayId; }
    public String getWxTransactionId() { return wxTransactionId; }
    public String getSn() { return sn; }
    public LocalDateTime getCooldownEndAt() { return cooldownEndAt; }
    public Long getInviterId() { return inviterId; }
    public Long getGrandInviterId() { return grandInviterId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}
