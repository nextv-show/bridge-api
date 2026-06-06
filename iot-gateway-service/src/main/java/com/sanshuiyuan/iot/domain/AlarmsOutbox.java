package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 告警发件箱（outbox）。每条告警落库后写一行，供 008（通知/工单）异步消费；
 * {@code consumedAt} 非空表示已消费。
 */
@Entity
@Table(name = "alarms_outbox")
public class AlarmsOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_id", nullable = false, unique = true)
    private Long alarmId;

    @Column(name = "payload_json", columnDefinition = "JSON", nullable = false)
    private String payloadJson;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AlarmsOutbox() {
    }

    public AlarmsOutbox(Long alarmId, String payloadJson) {
        this.alarmId = alarmId;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public Long getAlarmId() { return alarmId; }
    public String getPayloadJson() { return payloadJson; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
}
