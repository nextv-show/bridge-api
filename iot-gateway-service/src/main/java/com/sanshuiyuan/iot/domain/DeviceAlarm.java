package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 设备告警。{@code externalEventId} 为设备上报事件的幂等键（唯一）；{@code resolvedAt} 非空表示已闭环。
 */
@Entity
@Table(name = "device_alarms")
public class DeviceAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sn;

    @Enumerated(EnumType.STRING)
    @Column(name = "alarm_type", nullable = false)
    private AlarmType alarmType;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "external_event_id")
    private String externalEventId;

    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;

    @Column(name = "raised_at", nullable = false)
    private LocalDateTime raisedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected DeviceAlarm() {
    }

    public DeviceAlarm(String sn, AlarmType alarmType, Severity severity,
                       String externalEventId, String payloadJson, LocalDateTime raisedAt) {
        this.sn = sn;
        this.alarmType = alarmType;
        this.severity = severity;
        this.externalEventId = externalEventId;
        this.payloadJson = payloadJson;
        this.raisedAt = raisedAt;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public AlarmType getAlarmType() { return alarmType; }
    public Severity getSeverity() { return severity; }
    public String getExternalEventId() { return externalEventId; }
    public String getPayloadJson() { return payloadJson; }
    public LocalDateTime getRaisedAt() { return raisedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
