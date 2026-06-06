package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账告警：某日资金对账不平时落库的告警记录。 */
@Entity
@Table(name = "reconciliation_alerts")
public class ReconciliationAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "diff_cents", nullable = false)
    private Long diffCents;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected ReconciliationAlert() {}

    public ReconciliationAlert(LocalDate date, Long diffCents, String payloadJson, ReconciliationStatus status) {
        this.date = date;
        this.diffCents = diffCents;
        this.payloadJson = payloadJson;
        this.status = status;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Long getDiffCents() { return diffCents; }
    public void setDiffCents(Long diffCents) { this.diffCents = diffCents; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
