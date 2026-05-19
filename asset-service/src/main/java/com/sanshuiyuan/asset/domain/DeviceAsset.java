package com.sanshuiyuan.asset.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_assets")
public class DeviceAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(unique = true, length = 64)
    private String sn;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Stage stage;

    @Column(nullable = false)
    private Long cumulativeIncomeCents = 0L;

    @Column(nullable = false)
    private Integer roiBp = 0;

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
    public LocalDateTime getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(LocalDateTime purchasedAt) { this.purchasedAt = purchasedAt; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public Long getCumulativeIncomeCents() { return cumulativeIncomeCents; }
    public void setCumulativeIncomeCents(Long cumulativeIncomeCents) { this.cumulativeIncomeCents = cumulativeIncomeCents; }
    public Integer getRoiBp() { return roiBp; }
    public void setRoiBp(Integer roiBp) { this.roiBp = roiBp; }
}
