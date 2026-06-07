package com.sanshuiyuan.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 物流状态流水（logistics_events，落 core_db）。external_event_id 唯一索引兜底幂等。 */
@Entity
@Table(name = "logistics_events")
public class LogisticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "logistics_order_id", nullable = false)
    private Long logisticsOrderId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "external_event_id", length = 128)
    private String externalEventId;

    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLogisticsOrderId() { return logisticsOrderId; }
    public void setLogisticsOrderId(Long logisticsOrderId) { this.logisticsOrderId = logisticsOrderId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
}
