package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 设备资产（core_db.device_assets，本表为全系统真表——admin V074 建，admin/h5 共用；
 * asset_db.device_assets 为废弃旧表，见 024）。认购支付完成由 024 在 PAID 事务建 PENDING_MATCH 行。
 */
@Entity
@Table(name = "device_assets")
public class DeviceAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 认购单台幂等键（024：UNIQUE(order_id)，迁移 V079）。
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // SN 待绑定时为 null（024：sn 改可空，由 BindSnUseCase 后续绑定）。
    @Column(length = 64, unique = true)
    private String sn;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;

    @Column(name = "cumulative_income_cents", nullable = false)
    private Long cumulativeIncomeCents = 0L;

    @Column(name = "roi_bp", nullable = false)
    private Integer roiBp = 0;

    public enum Stage {
        PENDING_MATCH, PENDING_ACTIVATE, STAGE_1, STAGE_2, FUSED
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public String getSn() { return sn; }
    public String getModel() { return model; }
    public Stage getStage() { return stage; }
    public LocalDateTime getPurchasedAt() { return purchasedAt; }
    public Long getCumulativeIncomeCents() { return cumulativeIncomeCents; }
    public Integer getRoiBp() { return roiBp; }

    public void setSn(String sn) { this.sn = sn; }
}
