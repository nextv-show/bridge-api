package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "channel", length = 16)
    private String channel;

    @Column(name = "payment_method", length = 16)
    private String paymentMethod;

    @Column(name = "wx_transaction_id", length = 64)
    private String wxTransactionId;

    @Column(name = "device_asset_id")
    private Long deviceAssetId;

    @Column(name = "address_snapshot", columnDefinition = "JSON", nullable = false)
    private String addressSnapshot = "{}";

    @Column(name = "shipping_no", length = 64)
    private String shippingNo;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_deadline_at")
    private LocalDateTime paymentDeadlineAt;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING_PAY, PAID, SHIPPED, ACTIVATED, COMPLETED, REFUNDING, REFUNDED, CANCELLED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getWxTransactionId() { return wxTransactionId; }
    public void setWxTransactionId(String wxTransactionId) { this.wxTransactionId = wxTransactionId; }

    public Long getDeviceAssetId() { return deviceAssetId; }
    public void setDeviceAssetId(Long deviceAssetId) { this.deviceAssetId = deviceAssetId; }

    public String getAddressSnapshot() { return addressSnapshot; }
    public void setAddressSnapshot(String addressSnapshot) { this.addressSnapshot = addressSnapshot; }

    public String getShippingNo() { return shippingNo; }
    public void setShippingNo(String shippingNo) { this.shippingNo = shippingNo; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getPaymentDeadlineAt() { return paymentDeadlineAt; }
    public void setPaymentDeadlineAt(LocalDateTime paymentDeadlineAt) { this.paymentDeadlineAt = paymentDeadlineAt; }

    public LocalDateTime getLastRemindedAt() { return lastRemindedAt; }
    public void setLastRemindedAt(LocalDateTime lastRemindedAt) { this.lastRemindedAt = lastRemindedAt; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.addressSnapshot == null || this.addressSnapshot.isBlank()) {
            this.addressSnapshot = "{}";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
