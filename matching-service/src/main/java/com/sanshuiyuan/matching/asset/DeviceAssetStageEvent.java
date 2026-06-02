package com.sanshuiyuan.matching.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 设备阶段事件流（device_assets_stage_events，落 h5_db）。V015 建表。 */
@Entity
@Table(name = "device_assets_stage_events")
public class DeviceAssetStageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_asset_id", nullable = false)
    private Long deviceAssetId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeviceAssetId() { return deviceAssetId; }
    public void setDeviceAssetId(Long deviceAssetId) { this.deviceAssetId = deviceAssetId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
}
