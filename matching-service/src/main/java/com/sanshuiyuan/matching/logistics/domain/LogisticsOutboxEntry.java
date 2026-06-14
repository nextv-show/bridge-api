package com.sanshuiyuan.matching.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 物流 outbox（logistics_outbox，落 core_db）。 */
@Entity
@Table(name = "logistics_outbox")
public class LogisticsOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")   // SELF_USE 场景下为 null（设备自用，无撮合需求）
    private Long requestId;

    @Column(name = "device_asset_id", nullable = false)
    private Long deviceAssetId;

    @Column(name = "source", nullable = false)
    private String source = "MATCHING";

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Generated(event = EventType.INSERT)   // DB 默认 CURRENT_TIMESTAMP 写入后回读入实体
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getDeviceAssetId() { return deviceAssetId; }
    public void setDeviceAssetId(Long deviceAssetId) { this.deviceAssetId = deviceAssetId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
