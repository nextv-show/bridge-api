package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 流量遥测采样。{@code litersMilli} 为本次采样的累计毫升数，{@code deltaMilli} 为相对上次的增量。
 */
@Entity
@Table(name = "device_telemetry_flow")
public class TelemetrySampleFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sn;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "liters_milli", nullable = false)
    private Long litersMilli;

    @Column(name = "delta_milli", nullable = false)
    private Long deltaMilli;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected TelemetrySampleFlow() {
    }

    public TelemetrySampleFlow(String sn, Long sessionId, Long litersMilli, Long deltaMilli, LocalDateTime sampledAt) {
        this.sn = sn;
        this.sessionId = sessionId;
        this.litersMilli = litersMilli;
        this.deltaMilli = deltaMilli;
        this.sampledAt = sampledAt;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public Long getSessionId() { return sessionId; }
    public Long getLitersMilli() { return litersMilli; }
    public Long getDeltaMilli() { return deltaMilli; }
    public LocalDateTime getSampledAt() { return sampledAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
