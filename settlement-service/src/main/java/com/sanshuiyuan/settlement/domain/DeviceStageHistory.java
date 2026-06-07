package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 设备阶段跃迁历史：累计 ROI 达阈值时设备从一个阶段进入下一阶段。 */
@Entity
@Table(name = "device_stage_history")
public class DeviceStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sn;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", nullable = false)
    private DeviceStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false)
    private DeviceStage toStage;

    @Column(name = "at_bill_id", nullable = false)
    private Long atBillId;

    @Column(name = "at_roi_bp", nullable = false)
    private Integer atRoiBp;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected DeviceStageHistory() {}

    public DeviceStageHistory(String sn, DeviceStage fromStage, DeviceStage toStage, Long atBillId, Integer atRoiBp) {
        this.sn = sn;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.atBillId = atBillId;
        this.atRoiBp = atRoiBp;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public DeviceStage getFromStage() { return fromStage; }
    public void setFromStage(DeviceStage fromStage) { this.fromStage = fromStage; }
    public DeviceStage getToStage() { return toStage; }
    public void setToStage(DeviceStage toStage) { this.toStage = toStage; }
    public Long getAtBillId() { return atBillId; }
    public void setAtBillId(Long atBillId) { this.atBillId = atBillId; }
    public Integer getAtRoiBp() { return atRoiBp; }
    public void setAtRoiBp(Integer atRoiBp) { this.atRoiBp = atRoiBp; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
