package com.sanshuiyuan.settlement.infra.asset;

import com.sanshuiyuan.settlement.domain.DeviceStage;
import jakarta.persistence.*;

/**
 * h5_db.device_assets 读/写实体（含 V059 新列 purchase_price_cents / promoter_user_id）。
 *
 * <p>注意：真实 device_assets 表（admin V074 / asset V003 + settlement V059）没有 version 列，
 * 因此本实体不映射乐观锁 version；并发安全由结算时的 {@code findWithLockBySn}（SELECT ... FOR UPDATE）
 * 悲观行锁保证。
 */
@Entity
@Table(name = "device_assets")
public class DeviceAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(unique = true, length = 64)
    private String sn;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "purchased_at", nullable = false)
    private java.time.LocalDateTime purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DeviceStage stage;

    @Column(name = "cumulative_income_cents", nullable = false)
    private Long cumulativeIncomeCents = 0L;

    @Column(name = "roi_bp", nullable = false)
    private Integer roiBp = 0;

    @Column(name = "purchase_price_cents", nullable = false)
    private Long purchasePriceCents = 0L;

    @Column(name = "promoter_user_id")
    private Long promoterUserId;

    protected DeviceAssetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public java.time.LocalDateTime getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(java.time.LocalDateTime purchasedAt) { this.purchasedAt = purchasedAt; }

    public DeviceStage getStage() { return stage; }
    public void setStage(DeviceStage stage) { this.stage = stage; }

    public Long getCumulativeIncomeCents() { return cumulativeIncomeCents; }
    public void setCumulativeIncomeCents(Long cumulativeIncomeCents) { this.cumulativeIncomeCents = cumulativeIncomeCents; }

    public Integer getRoiBp() { return roiBp; }
    public void setRoiBp(Integer roiBp) { this.roiBp = roiBp; }

    public Long getPurchasePriceCents() { return purchasePriceCents; }
    public void setPurchasePriceCents(Long purchasePriceCents) { this.purchasePriceCents = purchasePriceCents; }

    public Long getPromoterUserId() { return promoterUserId; }
    public void setPromoterUserId(Long promoterUserId) { this.promoterUserId = promoterUserId; }
}
