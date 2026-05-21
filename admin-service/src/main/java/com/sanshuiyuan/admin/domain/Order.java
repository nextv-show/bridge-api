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

    @Column(name = "wx_transaction_id", length = 64)
    private String wxTransactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public enum Status {
        PENDING_PAY, PAID, CLOSED, REFUND
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSkuId() { return skuId; }
    public Integer getQty() { return qty; }
    public Long getAmountCents() { return amountCents; }
    public Status getStatus() { return status; }
    public String getWxTransactionId() { return wxTransactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
