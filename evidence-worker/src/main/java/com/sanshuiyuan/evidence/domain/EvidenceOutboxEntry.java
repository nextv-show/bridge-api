package com.sanshuiyuan.evidence.domain;

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
 * 存证发件箱（evidence-worker 侧映射，共享 water_db.evidence_outbox 表）。
 * 轮询取 status=PENDING 且 next_run_at 到期的记录，上链后置 DONE，失败按退避重排或置 FAILED。
 */
@Entity
@Table(name = "evidence_outbox")
public class EvidenceOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, insertable = false, updatable = false)
    private Long billId;

    @Column(name = "payload_json", columnDefinition = "JSON", nullable = false, insertable = false, updatable = false)
    private String payloadJson;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "retried", nullable = false)
    private Integer retried = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    protected EvidenceOutboxEntry() {
    }

    public Long getId() { return id; }
    public Long getBillId() { return billId; }
    public String getPayloadJson() { return payloadJson; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public Integer getRetried() { return retried; }
    public OutboxStatus getStatus() { return status; }

    /** 存证发件箱状态。 */
    public enum OutboxStatus {
        PENDING, DONE, FAILED
    }
}
