package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 滤芯寿命遥测采样。{@code lifePercent} 为剩余寿命百分比（0-100）。 */
@Entity
@Table(name = "device_telemetry_filter")
public class TelemetrySampleFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sn;

    @Column(name = "life_percent", nullable = false)
    private Short lifePercent;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected TelemetrySampleFilter() {
    }

    public TelemetrySampleFilter(String sn, Short lifePercent, LocalDateTime sampledAt) {
        this.sn = sn;
        this.lifePercent = lifePercent;
        this.sampledAt = sampledAt;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public Short getLifePercent() { return lifePercent; }
    public LocalDateTime getSampledAt() { return sampledAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
