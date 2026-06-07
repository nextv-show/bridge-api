package com.sanshuiyuan.water.session.domain;

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
 * 存证发件箱（outbox）。每条账单结算后落一行，供 evidence-worker 异步上链；
 * bill_id UNIQUE 保证一账单只投递一次。status=DONE 表示已处理。
 */
@Entity
@Table(name = "evidence_outbox")
public class EvidenceOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, unique = true)
    private Long billId;

    @Column(name = "payload_json", columnDefinition = "JSON", nullable = false)
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

    public static EvidenceOutboxEntry of(Long billId, String payloadJson, LocalDateTime nextRunAt) {
        EvidenceOutboxEntry e = new EvidenceOutboxEntry();
        e.billId = billId;
        e.payloadJson = payloadJson;
        e.nextRunAt = nextRunAt;
        e.retried = 0;
        e.status = OutboxStatus.PENDING;
        return e;
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
