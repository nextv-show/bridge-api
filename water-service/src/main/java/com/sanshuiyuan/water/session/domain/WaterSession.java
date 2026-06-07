package com.sanshuiyuan.water.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 取水会话（出水主链路核心聚合）。每次出水开启一行 {@code status=ACTIVE}，结算后置 {@code CLOSED}。
 * 唯一键 uk_sn_active(sn, status) 保证同一 SN 同时只有一个 ACTIVE 会话。
 * 结算字段(ended_at, total_liters_milli, total_amount_cents, end_reason)由 SettleWaterSessionUseCase 经原生 UPDATE 写入。
 */
@Entity
@Table(name = "water_sessions")
public class WaterSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sn", nullable = false)
    private String sn;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", insertable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", insertable = false, updatable = false)
    private LocalDateTime endedAt;

    @Column(name = "total_liters_milli", nullable = false)
    private Long totalLitersMilli = 0L;

    @Column(name = "total_amount_cents", nullable = false)
    private Long totalAmountCents = 0L;

    @Column(name = "price_per_liter_cents", nullable = false)
    private Integer pricePerLiterCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private EndReason endReason;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    protected WaterSession() {
    }

    /** 开启一个 ACTIVE 会话。started_at / 累计量取 DB 默认。 */
    public static WaterSession create(String sn, Long userId, int pricePerLiterCents) {
        WaterSession s = new WaterSession();
        s.sn = sn;
        s.userId = userId;
        s.pricePerLiterCents = pricePerLiterCents;
        s.status = SessionStatus.ACTIVE;
        s.totalLitersMilli = 0L;
        s.totalAmountCents = 0L;
        return s;
    }

    public Long getId() { return id; }
    public String getSn() { return sn; }
    public Long getUserId() { return userId; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public Long getTotalLitersMilli() { return totalLitersMilli; }
    public Long getTotalAmountCents() { return totalAmountCents; }
    public Integer getPricePerLiterCents() { return pricePerLiterCents; }
    public EndReason getEndReason() { return endReason; }
    public Integer getVersion() { return version; }
}
