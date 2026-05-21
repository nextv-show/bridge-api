package com.sanshuiyuan.admin.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_assets")
public class DeviceAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

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

    public void setSn(String sn) { this.sn = sn; }
}
