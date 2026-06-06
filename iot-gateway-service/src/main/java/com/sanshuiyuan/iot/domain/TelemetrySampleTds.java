package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** TDS（水质）遥测采样。 */
@Entity
@Table(name = "device_telemetry_tds")
public class TelemetrySampleTds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sn;

    @Column(name = "tds_value", nullable = false)
    private Integer tdsValue;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected TelemetrySampleTds() {
    }

    public TelemetrySampleTds(String sn, Integer tdsValue, LocalDateTime sampledAt) {
        this.sn = sn;
        this.tdsValue = tdsValue;
        this.sampledAt = sampledAt;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public Integer getTdsValue() { return tdsValue; }
    public LocalDateTime getSampledAt() { return sampledAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
