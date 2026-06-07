package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 事务发件箱：领域事件可靠投递，幂等键去重 + 退避重试。 */
@Entity
@Table(name = "settlement_outbox")
public class SettlementOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OutboxEventType eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retried;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected SettlementOutbox() {}

    public SettlementOutbox(String aggregateType, String aggregateId, OutboxEventType eventType, String payloadJson,
                            String idempotencyKey, OutboxStatus status, Integer retried, LocalDateTime nextRunAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.retried = retried;
        this.nextRunAt = nextRunAt;
    }

    public Long getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public OutboxEventType getEventType() { return eventType; }
    public void setEventType(OutboxEventType eventType) { this.eventType = eventType; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public Integer getRetried() { return retried; }
    public void setRetried(Integer retried) { this.retried = retried; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
