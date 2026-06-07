package com.sanshuiyuan.water.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 控制面 outbox 事件（mock）。{@code consumedByWater} 非空表示已被 water-service 消费。
 * V1 与 water_db 同库（生产应在独立控制面库），由 DevicePermissionProjector 轮询消费。
 */
@Entity
@Table(name = "device_control_events")
public class DeviceControlEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sn;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "can_dispense", nullable = false)
    private boolean canDispense;

    private String reason;

    @Column(name = "consumed_by_water")
    private LocalDateTime consumedByWater;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DeviceControlEvent() {
    }

    public DeviceControlEvent(String sn, String eventType, boolean canDispense, String reason) {
        this.sn = sn;
        this.eventType = eventType;
        this.canDispense = canDispense;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public String getEventType() { return eventType; }
    public boolean isCanDispense() { return canDispense; }
    public String getReason() { return reason; }
    public LocalDateTime getConsumedByWater() { return consumedByWater; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setConsumedByWater(LocalDateTime consumedByWater) { this.consumedByWater = consumedByWater; }
}
